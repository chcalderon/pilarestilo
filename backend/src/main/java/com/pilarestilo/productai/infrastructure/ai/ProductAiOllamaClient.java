package com.pilarestilo.productai.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class ProductAiOllamaClient {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String baseUrl;
    private final String model;
    private final String systemPrompt;
    private final String fallbackStyleHint;
    private final String keepAlive;

    public ProductAiOllamaClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.product-ai.ollama.enabled:true}") boolean enabled,
            @Value("${app.product-ai.ollama.base-url:http://localhost:11434/api}") String baseUrl,
            @Value("${app.product-ai.ollama.model:gemma3}") String model,
            @Value("${app.product-ai.ollama.system-prompt:}") String systemPrompt,
            @Value("${app.product-ai.ollama.style-hint:boutique elegante, lujo accesible, segunda mano premium}") String fallbackStyleHint,
            @Value("${app.product-ai.ollama.keep-alive:45m}") String keepAlive
    ) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.fallbackStyleHint = fallbackStyleHint;
        this.keepAlive = keepAlive;
    }

    public InferenceResult inferFromImage(byte[] imageBytes, String sourceFilename, String brandHint) {
        if (!enabled) {
            return fallbackFromFilename(sourceFilename, brandHint, "ollama-disabled");
        }
        if (imageBytes == null || imageBytes.length == 0) {
            return fallbackFromFilename(sourceFilename, brandHint, "empty-image");
        }

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String prompt = buildUserPrompt(sourceFilename, brandHint);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("stream", false);
        payload.put("format", "json");
        payload.put("keep_alive", keepAlive);
        payload.put("messages", List.of(Map.of(
                "role", "user",
                "content", prompt,
                "images", List.of(base64Image)
        )));
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            payload.put("options", Map.of("num_predict", 420));
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", prompt, "images", List.of(base64Image))
            ));
        }

        try {
            RestClient restClient = restClientBuilder.baseUrl(baseUrl).build();
            JsonNode responseNode = restClient.post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            if (responseNode == null) {
                return fallbackFromFilename(sourceFilename, brandHint, "empty-ollama-response");
            }
            String rawTextJson = responseNode.path("message").path("content").asText("");
            if (rawTextJson.isBlank()) {
                return fallbackFromFilename(sourceFilename, brandHint, "missing-ollama-content");
            }
            JsonNode parsed;
            try {
                parsed = objectMapper.readTree(rawTextJson);
            } catch (Exception ex) {
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
            return fallbackFromFilename(sourceFilename, brandHint, "ollama-error");
        }
    }

    public ReadinessStatus checkReadiness() {
        if (!enabled) {
            return new ReadinessStatus(true, true, true, baseUrl, model, "ollama-disabled");
        }
        try {
            RestClient restClient = restClientBuilder.baseUrl(baseUrl).build();
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
            RestClient restClient = restClientBuilder.baseUrl(baseUrl).build();
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

    private String buildUserPrompt(String sourceFilename, String brandHint) {
        return """
                Analiza la imagen de una prenda para ecommerce de moda boutique.
                Responde SOLO JSON con llaves: title, description, imagePrompt.
                Reglas:
                - Español chileno neutro, sin emojis.
                - title: corto, comercial, max 90 chars, sin simbolos raros.
                - description: 2 frases maximo, orientada a venta.
                - imagePrompt: instruccion para editar imagen con modelo femenina elegante, fondo exterior boutique, formato 4:5.
                - En imagePrompt obliga: sin texto, sin logos, sin marcas de agua, fidelidad estricta de color/textura/diseno/corte de la prenda.
                - Si no estas seguro de una talla o marca, no inventes.
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
}
