package com.pilarestilo.productai.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProductAiOllamaClient {

    private static final Logger log = LoggerFactory.getLogger(ProductAiOllamaClient.class);

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final boolean enabled;
    private final String baseUrl;
    private final String model;
    private final String systemPrompt;
    private final String fallbackStyleHint;
    private final String keepAlive;
    private final int numPredict;
    private final int numCtx;
    private final double temperature;

    public ProductAiOllamaClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.product-ai.ollama.enabled:true}") boolean enabled,
            @Value("${app.product-ai.ollama.base-url:http://localhost:11434/api}") String baseUrl,
            @Value("${app.product-ai.ollama.model:gemma3}") String model,
            @Value("${app.product-ai.ollama.system-prompt:}") String systemPrompt,
            @Value("${app.product-ai.ollama.style-hint:boutique elegante, lujo accesible, segunda mano premium}") String fallbackStyleHint,
            @Value("${app.product-ai.ollama.keep-alive:45m}") String keepAlive,
            @Value("${app.product-ai.ollama.num-predict:220}") int numPredict,
            @Value("${app.product-ai.ollama.num-ctx:2048}") int numCtx,
            @Value("${app.product-ai.ollama.temperature:0.2}") double temperature,
            @Value("${app.product-ai.timeout-ms:60000}") long timeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.fallbackStyleHint = fallbackStyleHint;
        this.keepAlive = keepAlive;
        this.numPredict = Math.max(32, numPredict);
        this.numCtx = Math.max(512, numCtx);
        this.temperature = Math.max(0.0d, Math.min(1.0d, temperature));

        int safeTimeoutMs = (int) Math.max(5_000L, Math.min(timeoutMs, Integer.MAX_VALUE));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(safeTimeoutMs);
        requestFactory.setReadTimeout(safeTimeoutMs);
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public InferenceResult inferFromImage(byte[] imageBytes, String sourceFilename, String brandHint) {
        return inferFromImage(imageBytes, sourceFilename, brandHint, null);
    }

    public InferenceResult inferFromImage(byte[] imageBytes, String sourceFilename, String brandHint, String modelOverride) {
        if (!enabled) {
            return fallbackFromFilename(sourceFilename, brandHint, "ollama-disabled");
        }
        if (imageBytes == null || imageBytes.length == 0) {
            return fallbackFromFilename(sourceFilename, brandHint, "empty-image");
        }

        String requestedModel = resolveRequestedModel(modelOverride);
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String prompt = buildUserPrompt(sourceFilename, brandHint);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", requestedModel);
        payload.put("stream", false);
        payload.put("format", responseSchema());
        payload.put("keep_alive", keepAlive);
        payload.put("options", Map.of(
                "num_predict", numPredict,
                "num_ctx", numCtx,
                "temperature", temperature
        ));
        payload.put("messages", List.of(Map.of(
                "role", "user",
                "content", prompt,
                "images", List.of(base64Image)
        )));
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", prompt, "images", List.of(base64Image))
            ));
        }

        try {
            JsonNode responseNode = restClient.post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            if (responseNode == null) {
                return fallbackFromFilename(sourceFilename, brandHint, "empty-ollama-response");
            }
            if ("length".equalsIgnoreCase(responseNode.path("done_reason").asText(""))) {
                return fallbackFromFilename(sourceFilename, brandHint, "ollama-length");
            }
            JsonNode contentNode = responseNode.path("message").path("content");
            String rawTextJson;
            if (contentNode.isObject() || contentNode.isArray()) {
                rawTextJson = contentNode.toString();
            } else {
                rawTextJson = contentNode.asText("");
            }
            rawTextJson = normalizePotentialJson(rawTextJson);
            if (rawTextJson.isBlank()) {
                return fallbackFromFilename(sourceFilename, brandHint, "missing-ollama-content");
            }
            JsonNode parsed = parseLenientJson(rawTextJson);
            if (parsed == null) {
                ParsedFields rescued = extractFieldsFromRawText(rawTextJson);
                if (rescued.hasAnyValue()) {
                    String rescuedTitle = normalizeTitle(rescued.title());
                    String rescuedDescription = normalizeDescription(rescued.description());
                    String rescuedImagePrompt = normalizePrompt(rescued.imagePrompt());

                    if (rescuedTitle.isBlank()) {
                        rescuedTitle = fallbackTitle(sourceFilename, brandHint);
                    }
                    if (rescuedDescription.isBlank()) {
                        rescuedDescription = "Prenda de boutique en excelente estado, lista para publicar.";
                    }
                    if (rescuedImagePrompt.isBlank()) {
                        rescuedImagePrompt = defaultImagePrompt(sourceFilename, brandHint);
                    }

                    log.warn(
                            "product_ai_ollama_non_json_rescued model={} done_reason={} preview={}",
                            requestedModel,
                            responseNode.path("done_reason").asText(""),
                            preview(rawTextJson)
                    );
                    return new InferenceResult(
                            rescuedTitle,
                            rescuedDescription,
                            rescuedImagePrompt,
                            rawTextJson,
                            "ollama",
                            "rescued-non-json"
                    );
                }

                log.warn(
                        "product_ai_ollama_invalid_json model={} done_reason={} preview={}",
                        requestedModel,
                        responseNode.path("done_reason").asText(""),
                        preview(rawTextJson)
                );
                return fallbackFromFilename(sourceFilename, brandHint, "invalid-ollama-json");
            }

            String title = normalizeTitle(parsed.path("title").asText(""));
            String description = normalizeDescription(parsed.path("description").asText(""));
            String imagePrompt = normalizePrompt(parsed.path("imagePrompt").asText(""));
            if (title.isBlank()) {
                title = fallbackTitle(sourceFilename, brandHint);
            }
            if (description.isBlank()) {
                description = "Prenda de boutique en excelente estado, lista para publicar.";
            }
            if (imagePrompt.isBlank()) {
                imagePrompt = defaultImagePrompt(sourceFilename, brandHint);
            }
            return new InferenceResult(title, description, imagePrompt, rawTextJson, "ollama", null);
        } catch (RestClientResponseException ex) {
            return fallbackFromFilename(sourceFilename, brandHint, "ollama-http-" + ex.getStatusCode().value());
        } catch (Exception ex) {
            return fallbackFromFilename(sourceFilename, brandHint, classifyClientErrorReason(ex));
        }
    }

    public ReadinessStatus checkReadiness() {
        if (!enabled) {
            return new ReadinessStatus(true, true, true, baseUrl, model, "ollama-disabled");
        }
        try {
            JsonNode responseNode = restClient.get()
                    .uri("/tags")
                    .retrieve()
                    .body(JsonNode.class);
            if (responseNode == null) {
                return new ReadinessStatus(false, true, false, baseUrl, model, "empty-ollama-tags-response");
            }

            JsonNode models = responseNode.path("models");
            if (!models.isArray()) {
                return new ReadinessStatus(false, true, false, baseUrl, model, "invalid-ollama-tags-payload");
            }

            boolean modelAvailable = false;
            for (JsonNode modelNode : models) {
                String modelName = modelNode.path("name").asText("");
                if (matchesModel(model, modelName)) {
                    modelAvailable = true;
                    break;
                }
            }
            if (!modelAvailable) {
                return new ReadinessStatus(false, true, false, baseUrl, model, "ollama-model-missing");
            }

            return new ReadinessStatus(true, true, true, baseUrl, model, "ok");
        } catch (RestClientResponseException ex) {
            return new ReadinessStatus(false, false, false, baseUrl, model, "ollama-http-" + ex.getStatusCode().value());
        } catch (Exception ex) {
            return new ReadinessStatus(false, false, false, baseUrl, model, "ollama-unreachable");
        }
    }

    public void warmUp() {
        if (!enabled) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("stream", false);
            payload.put("keep_alive", keepAlive);
            payload.put("messages", List.of(Map.of(
                    "role", "user",
                    "content", "Responde solo: ok"
            )));
            restClient.post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ignored) {
            // Warm-up best effort. Main requests keep their own fallback/error handling.
        }
    }

    private String classifyClientErrorReason(Exception ex) {
        if (ex == null) {
            return "ollama-error";
        }
        String className = ex.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        if (className.contains("timeout")
                || message.contains("timed out")
                || message.contains("timeout awaiting response headers")
                || message.contains("read timed out")) {
            return "ollama-timeout";
        }
        return "ollama-error";
    }

    private Map<String, Object> responseSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "title", Map.of("type", "string"),
                "description", Map.of("type", "string"),
                "imagePrompt", Map.of("type", "string")
        ));
        schema.put("required", List.of("title", "description", "imagePrompt"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private String normalizePotentialJson(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized
                    .replace("```json", "")
                    .replace("```JSON", "")
                    .replace("```", "")
                    .trim();
        }
        int firstObject = normalized.indexOf('{');
        int lastObject = normalized.lastIndexOf('}');
        if (firstObject >= 0 && lastObject > firstObject) {
            String candidate = normalized.substring(firstObject, lastObject + 1).trim();
            if (!candidate.isBlank()) {
                return candidate;
            }
        }
        return normalized;
    }

    private JsonNode parseLenientJson(String rawTextJson) {
        JsonNode direct = tryReadTree(rawTextJson);
        if (direct != null) {
            return unwrapCommonPayload(direct);
        }

        String relaxed = relaxJson(rawTextJson);
        JsonNode relaxedNode = tryReadTree(relaxed);
        if (relaxedNode != null) {
            return unwrapCommonPayload(relaxedNode);
        }
        return null;
    }

    private JsonNode unwrapCommonPayload(JsonNode node) {
        if (node == null) {
            return null;
        }
        JsonNode current = node;
        if (current.isTextual()) {
            JsonNode nested = tryReadTree(current.asText(""));
            if (nested != null) {
                current = nested;
            }
        }
        if (current.path("response").isObject()) {
            return current.path("response");
        }
        if (current.path("data").isObject()) {
            return current.path("data");
        }
        if (current.path("result").isObject()) {
            return current.path("result");
        }
        return current;
    }

    private JsonNode tryReadTree(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String relaxJson(String input) {
        if (input == null) {
            return "";
        }
        String relaxed = input.trim();
        relaxed = relaxed.replaceAll(",\\s*([}\\]])", "$1");
        relaxed = relaxed.replace('\'', '"');
        return relaxed;
    }

    private ParsedFields extractFieldsFromRawText(String text) {
        if (text == null || text.isBlank()) {
            return ParsedFields.empty();
        }

        String title = extractLabeledValue(text, "(?im)^(?:title|titulo|nombre)\\s*[:=\\-]\\s*(.+)$");
        String description = extractLabeledValue(text, "(?im)^(?:description|descripcion)\\s*[:=\\-]\\s*(.+)$");
        String imagePrompt = extractLabeledValue(text, "(?im)^(?:imageprompt|image_prompt|promptimagen|prompt_imagen|prompt)\\s*[:=\\-]\\s*(.+)$");

        if (title.isBlank() && description.isBlank() && imagePrompt.isBlank()) {
            return ParsedFields.empty();
        }
        return new ParsedFields(title, description, imagePrompt);
    }

    private String extractLabeledValue(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        String value = matcher.group(1);
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 220) {
            return compact;
        }
        return compact.substring(0, 220) + "...";
    }

    private boolean matchesModel(String requestedModel, String availableModel) {
        String requested = normalizeModelName(requestedModel);
        String available = normalizeModelName(availableModel);
        if (requested.isBlank() || available.isBlank()) {
            return false;
        }
        if (requested.equals(available)) {
            return true;
        }
        if (available.startsWith(requested + ":") || requested.startsWith(available + ":")) {
            return true;
        }
        String requestedBase = requested.split(":", 2)[0];
        String availableBase = available.split(":", 2)[0];
        return Objects.equals(requestedBase, availableBase);
    }

    private String normalizeModelName(String modelName) {
        if (modelName == null) {
            return "";
        }
        return modelName.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveRequestedModel(String modelOverride) {
        if (modelOverride == null || modelOverride.isBlank()) {
            return model;
        }
        return modelOverride.trim();
    }

    private String buildUserPrompt(String sourceFilename, String brandHint) {
        return """
                Analiza la imagen de una prenda para ecommerce de moda boutique.
                Responde SOLO JSON con llaves: title, description, imagePrompt.
                Reglas:
                - Espanol chileno neutro, sin emojis.
                - title: comercial, concreto, entre 2 y 7 palabras, max 90 chars, sin simbolos raros.
                - description: exactamente 2 frases completas, 20-45 palabras en total, orientada a venta.
                - imagePrompt: instruccion para editar imagen con modelo femenina elegante, fondo exterior boutique, formato 4:5.
                - En imagePrompt obliga: sin texto, sin logos, sin marcas de agua, fidelidad estricta de color/textura/diseno/corte de la prenda.
                - Si no estas seguro de una talla o marca, no inventes.
                - No escribas metainstrucciones ni frases como "corto", "2 frases", "maximo", "completa", "placeholder" o "N/A".
                - Si no puedes inferir bien, entrega una propuesta comercial util, nunca instrucciones para otro modelo.
                Contexto:
                - archivo fuente: %s
                - marca sugerida: %s
                - estilo tienda: %s
                """.formatted(
                sourceFilename == null ? "sin-nombre" : sourceFilename,
                brandHint == null || brandHint.isBlank() ? "sin-marca" : brandHint,
                fallbackStyleHint
        );
    }

    private InferenceResult fallbackFromFilename(String sourceFilename, String brandHint, String reason) {
        String title = fallbackTitle(sourceFilename, brandHint);
        String description = "Prenda seleccionada para catalogo boutique. Revisar y ajustar detalles finales antes de publicar.";
        String imagePrompt = defaultImagePrompt(sourceFilename, brandHint);
        String raw = "{\"fallback\":true,\"reason\":\"" + reason + "\"}";
        return new InferenceResult(title, description, imagePrompt, raw, "ollama-fallback", reason);
    }

    private String fallbackTitle(String sourceFilename, String brandHint) {
        String source = sourceFilename == null ? "" : sourceFilename;
        int dot = source.lastIndexOf('.');
        if (dot > 0) {
            source = source.substring(0, dot);
        }
        source = source.replace('_', ' ').replace('-', ' ').trim();
        if (source.isBlank()) {
            source = "Prenda boutique";
        }
        if (brandHint != null && !brandHint.isBlank() && !source.toLowerCase(Locale.ROOT).contains(brandHint.toLowerCase(Locale.ROOT))) {
            source = source + " " + brandHint.trim();
        }
        if (source.length() > 90) {
            source = source.substring(0, 90).trim();
        }
        return Character.toUpperCase(source.charAt(0)) + source.substring(1);
    }

    private String defaultImagePrompt(String sourceFilename, String brandHint) {
        String garmentHint = fallbackTitle(sourceFilename, brandHint).toLowerCase(Locale.ROOT);
        return "Transformar imagen de " + garmentHint
                + " a estilo editorial boutique exterior premium, modelo femenina elegante, luz natural calida, fondo refinado con bokeh, formato 4:5, sin texto, sin logos, sin marcas de agua, manteniendo fidelidad estricta de color, textura, diseno y corte.";
    }

    private String normalizeTitle(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.length() > 90) {
            value = value.substring(0, 90).trim();
        }
        return value;
    }

    private String normalizeDescription(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.length() > 420) {
            value = value.substring(0, 420).trim();
        }
        return value;
    }

    private String normalizePrompt(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.length() > 1600) {
            value = value.substring(0, 1600).trim();
        }
        return value;
    }

    public record InferenceResult(
            String title,
            String description,
            String imagePrompt,
            String rawResponseJson,
            String engine,
            String fallbackReason
    ) {
    }

    public record ReadinessStatus(
            boolean ready,
            boolean reachable,
            boolean modelAvailable,
            String baseUrl,
            String model,
            String reason
    ) {
    }

    private record ParsedFields(
            String title,
            String description,
            String imagePrompt
    ) {
        static ParsedFields empty() {
            return new ParsedFields("", "", "");
        }

        boolean hasAnyValue() {
            return (title != null && !title.isBlank())
                    || (description != null && !description.isBlank())
                    || (imagePrompt != null && !imagePrompt.isBlank());
        }
    }
}
