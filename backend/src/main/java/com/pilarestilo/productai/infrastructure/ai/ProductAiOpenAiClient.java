package com.pilarestilo.productai.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pilarestilo.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProductAiOpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(ProductAiOpenAiClient.class);

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public ProductAiOpenAiClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.product-ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.product-ai.openai.api-key:}") String apiKey,
            @Value("${app.product-ai.openai.model:gpt-image-1}") String model,
            @Value("${app.product-ai.timeout-ms:180000}") long timeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gpt-image-1" : model.trim();

        int safeTimeoutMs = (int) Math.max(10_000L, Math.min(timeoutMs, Integer.MAX_VALUE));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(safeTimeoutMs);
        requestFactory.setReadTimeout(safeTimeoutMs);
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public InferenceResult inferFromImage(byte[] imageBytes, String sourceFilename, String brandHint) {
        requireConfigured();
        if (imageBytes == null || imageBytes.length == 0) {
            throw new DomainException("No se recibio imagen para inferencia IA");
        }

        String prompt = buildUserPrompt(sourceFilename, brandHint);
        String encoded = Base64.getEncoder().encodeToString(imageBytes);
        String imageDataUrl = "data:image/jpeg;base64," + encoded;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("input", List.of(Map.of(
                "role", "user",
                "content", List.of(
                        Map.of("type", "input_text", "text", prompt),
                        Map.of("type", "input_image", "image_url", imageDataUrl, "detail", "high")
                )
        )));
        payload.put("text", Map.of("format", Map.of("type", "text")));

        try {
            JsonNode responseNode = restClient.post()
                    .uri("/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);

            if (responseNode == null) {
                throw new DomainException("OpenAI devolvio respuesta vacia");
            }

            String rawText = extractOutputText(responseNode);
            if (rawText.isBlank()) {
                throw new DomainException("OpenAI no devolvio texto util para inferencia");
            }

            JsonNode parsed = parseLenientJson(rawText);
            ParsedFields fields;
            if (parsed != null) {
                fields = new ParsedFields(
                        parsed.path("title").asText(""),
                        parsed.path("description").asText(""),
                        parsed.path("imagePrompt").asText("")
                );
            } else {
                fields = extractFieldsFromRawText(rawText);
            }

            String title = normalizeTitle(fields.title());
            String description = normalizeDescription(fields.description());
            String imagePrompt = normalizePrompt(fields.imagePrompt());
            if (title.isBlank()) {
                title = fallbackTitle(sourceFilename, brandHint);
            }
            if (description.isBlank()) {
                description = "Prenda de boutique en excelente estado, lista para publicar.";
            }
            if (imagePrompt.isBlank()) {
                imagePrompt = defaultImagePrompt(sourceFilename, brandHint);
            }

            return new InferenceResult(
                    title,
                    description,
                    imagePrompt,
                    rawText,
                    "openai",
                    parsed == null ? "openai-non-json" : null
            );
        } catch (RestClientResponseException ex) {
            throw new DomainException("OpenAI inferencia fallo (" + ex.getStatusCode().value() + ")");
        } catch (DomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DomainException("OpenAI inferencia fallo: " + ex.getMessage());
        }
    }

    public byte[] transformImage(byte[] imageBytes, String sourceFilename, String prompt) {
        requireConfigured();
        if (imageBytes == null || imageBytes.length == 0) {
            throw new DomainException("No se recibio imagen para transformacion IA");
        }

        String normalizedPrompt = prompt == null ? "" : prompt.trim();
        if (normalizedPrompt.isBlank()) {
            normalizedPrompt = defaultImagePrompt(sourceFilename, null);
        }

        ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return sourceFilename == null || sourceFilename.isBlank() ? "product.jpg" : sourceFilename;
            }
        };

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model", model);
        form.add("prompt", normalizedPrompt);
        form.add("size", "1024x1536");
        form.add("quality", "medium");
        form.add("image", imageResource);

        try {
            JsonNode responseNode = restClient.post()
                    .uri("/images/edits")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

            if (responseNode == null) {
                throw new DomainException("OpenAI no devolvio imagen transformada");
            }
            JsonNode b64Node = responseNode.path("data").path(0).path("b64_json");
            if (b64Node.isMissingNode() || b64Node.asText("").isBlank()) {
                throw new DomainException("OpenAI no incluyo imagen transformada en la respuesta");
            }
            return Base64.getDecoder().decode(b64Node.asText("").getBytes(StandardCharsets.UTF_8));
        } catch (RestClientResponseException ex) {
            throw new DomainException("OpenAI transformacion fallo (" + ex.getStatusCode().value() + ")");
        } catch (DomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DomainException("OpenAI transformacion fallo: " + ex.getMessage());
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new DomainException("Falta configurar APP_PRODUCT_AI_OPENAI_API_KEY");
        }
    }

    private String extractOutputText(JsonNode responseNode) {
        String direct = responseNode.path("output_text").asText("");
        if (!direct.isBlank()) {
            return direct.trim();
        }

        StringBuilder builder = new StringBuilder();
        JsonNode outputArray = responseNode.path("output");
        if (!outputArray.isArray()) {
            return "";
        }
        for (JsonNode outputItem : outputArray) {
            JsonNode contentArray = outputItem.path("content");
            if (!contentArray.isArray()) {
                continue;
            }
            for (JsonNode content : contentArray) {
                String type = content.path("type").asText("");
                if ("output_text".equals(type) || "text".equals(type)) {
                    String text = content.path("text").asText("");
                    if (!text.isBlank()) {
                        if (builder.length() > 0) {
                            builder.append('\n');
                        }
                        builder.append(text.trim());
                    }
                }
            }
        }
        return builder.toString().trim();
    }

    private String buildUserPrompt(String sourceFilename, String brandHint) {
        return """
                Analiza la imagen de una prenda para ecommerce de moda boutique.
                Responde SOLO con JSON valido y exacto, sin markdown ni comentarios.
                Debes incluir llaves: title, description, imagePrompt.
                Reglas:
                - Espanol chileno neutro, sin emojis.
                - title: comercial y concreto, 2 a 7 palabras, max 90 caracteres.
                - description: exactamente 2 frases completas, entre 20 y 45 palabras totales.
                - imagePrompt: instruccion para editar imagen con modelo femenina elegante, fondo exterior boutique, formato vertical 4:5.
                - En imagePrompt incluye: sin texto, sin logos, sin marcas de agua, manteniendo fidelidad estricta de color, textura, diseno y corte de la prenda.
                - Si no estas seguro de talla o marca, no inventes.
                Contexto:
                - archivo fuente: %s
                - marca sugerida: %s
                Formato obligatorio:
                {"title":"...","description":"...","imagePrompt":"..."}
                """.formatted(
                sourceFilename == null ? "sin-nombre" : sourceFilename,
                brandHint == null || brandHint.isBlank() ? "sin-marca" : brandHint
        );
    }

    private JsonNode parseLenientJson(String rawTextJson) {
        JsonNode direct = tryReadTree(rawTextJson);
        if (direct != null) {
            return direct;
        }

        String relaxed = relaxJson(rawTextJson);
        JsonNode relaxedNode = tryReadTree(relaxed);
        if (relaxedNode != null) {
            return relaxedNode;
        }
        return null;
    }

    private JsonNode tryReadTree(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = normalizePotentialJson(text);
        try {
            return objectMapper.readTree(normalized);
        } catch (Exception ignored) {
            return null;
        }
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
        log.warn("product_ai_openai_non_json_rescued preview={}", preview(text));
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
        if (brandHint != null && !brandHint.isBlank()
                && !source.toLowerCase(Locale.ROOT).contains(brandHint.toLowerCase(Locale.ROOT))) {
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
            String warningReason
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
    }
}
