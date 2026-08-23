package com.pilarestilo.productai.infrastructure.ai;

import com.pilarestilo.shared.domain.DomainException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterization tests written before reducing inferFromImage()'s Cognitive Complexity (S3776)
 * -- it had none. A real local HttpServer stands in for the OpenAI endpoint: the client builds
 * its own RestClient internally from the injected RestClient.Builder (with a timeout-configured
 * request factory), so there is no seam for mocking the HTTP layer -- only a real socket call
 * exercises the actual code path.
 */
class ProductAiOpenAiClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private ProductAiOpenAiClient startServerAndBuildClient(String responseBody, int statusCode) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/responses", exchange -> respond(exchange, statusCode, responseBody));
        server.start();
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        return new ProductAiOpenAiClient(
                RestClient.builder(), new ObjectMapper(), baseUrl, "test-api-key",
                "gpt-4.1-mini", "gpt-image-1", "gpt-image-1", 15_000L);
    }

    private void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String outputTextResponse(String text) {
        return new ObjectMapper().writeValueAsString(Map.of("output_text", text));
    }

    @Test
    void parsesAValidJsonInferenceResponse() throws IOException {
        var client = startServerAndBuildClient(
                outputTextResponse("{\"title\":\"Blazer Zara\",\"description\":\"Elegante y comodo.\",\"imagePrompt\":\"foto editorial\"}"),
                200);

        var result = client.inferFromImage(new byte[]{1, 2, 3}, "foto.jpg", "Zara");

        assertThat(result.title()).isEqualTo("Blazer Zara");
        assertThat(result.description()).isEqualTo("Elegante y comodo.");
        assertThat(result.imagePrompt()).isEqualTo("foto editorial");
        assertThat(result.engine()).isEqualTo("openai");
        assertThat(result.warningReason()).isNull();
    }

    @Test
    void parsesJsonWrappedInMarkdownFences() throws IOException {
        var client = startServerAndBuildClient(
                outputTextResponse("```json\n{\"title\":\"Vestido\",\"description\":\"Corte recto.\",\"imagePrompt\":\"prompt x\"}\n```"),
                200);

        var result = client.inferFromImage(new byte[]{1}, "v.jpg", null);

        assertThat(result.title()).isEqualTo("Vestido");
        assertThat(result.warningReason()).isNull();
    }

    @Test
    void parsesJsonWithATrailingComma() throws IOException {
        var client = startServerAndBuildClient(
                outputTextResponse("{\"title\":\"Falda\",\"description\":\"Talle M.\",\"imagePrompt\":\"prompt y\",}"),
                200);

        var result = client.inferFromImage(new byte[]{1}, "f.jpg", null);

        assertThat(result.title()).isEqualTo("Falda");
        assertThat(result.warningReason()).isNull();
    }

    @Test
    void rescuesLabeledFieldsFromNonJsonText() throws IOException {
        var client = startServerAndBuildClient(
                outputTextResponse("title: Chaqueta Cuero\ndescription: Cuero genuino en buen estado.\nimagePrompt: fondo boutique"),
                200);

        var result = client.inferFromImage(new byte[]{1}, "c.jpg", null);

        assertThat(result.title()).isEqualTo("Chaqueta Cuero");
        assertThat(result.description()).isEqualTo("Cuero genuino en buen estado.");
        assertThat(result.imagePrompt()).isEqualTo("fondo boutique");
        assertThat(result.warningReason()).isEqualTo("openai-non-json");
    }

    @Test
    void fallsBackToDerivedFieldsWhenTextHasNoRecognizableStructure() throws IOException {
        var client = startServerAndBuildClient(outputTextResponse("respuesta completamente libre sin estructura"), 200);

        var result = client.inferFromImage(new byte[]{1}, "blazer_negro.jpg", "Zara");

        assertThat(result.title()).contains("Blazer negro").contains("Zara");
        assertThat(result.description()).isEqualTo("Prenda de boutique en excelente estado, lista para publicar.");
        assertThat(result.imagePrompt()).isNotBlank();
        assertThat(result.warningReason()).isEqualTo("openai-non-json");
    }

    @Test
    void assemblesTextFromTheOutputArrayWhenOutputTextIsAbsent() throws IOException {
        var mapper = new ObjectMapper();
        String innerJson = mapper.writeValueAsString(
                Map.of("title", "Pantalon", "description", "Recto y comodo.", "imagePrompt", "prompt z"));
        String body = mapper.writeValueAsString(Map.of(
                "output", List.of(Map.of("content", List.of(Map.of("type", "text", "text", innerJson))))));

        var client = startServerAndBuildClient(body, 200);

        var result = client.inferFromImage(new byte[]{1}, "p.jpg", null);

        assertThat(result.title()).isEqualTo("Pantalon");
    }

    @Test
    void blankOutputTextThrowsADomainException() throws IOException {
        var client = startServerAndBuildClient("{\"output_text\": \"\", \"output\": []}", 200);

        assertThatThrownBy(() -> client.inferFromImage(new byte[]{1}, "x.jpg", null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no devolvio texto util");
    }

    @Test
    void anErrorStatusIsTranslatedToADomainExceptionWithTheApiMessage() throws IOException {
        var client = startServerAndBuildClient(
                "{\"error\": {\"message\": \"rate limit exceeded\"}}", 429);

        assertThatThrownBy(() -> client.inferFromImage(new byte[]{1}, "x.jpg", null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("429")
                .hasMessageContaining("rate limit exceeded");
    }

    @Test
    void missingApiKeyThrowsWithoutCallingTheNetwork() {
        var client = new ProductAiOpenAiClient(
                RestClient.builder(), new ObjectMapper(), "http://localhost:1", "",
                "gpt-4.1-mini", "gpt-image-1", "gpt-image-1", 15_000L);

        assertThatThrownBy(() -> client.inferFromImage(new byte[]{1}, "x.jpg", null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("APP_PRODUCT_AI_OPENAI_API_KEY");
    }

    @Test
    void emptyImageBytesThrowsWithoutCallingTheNetwork() {
        var client = new ProductAiOpenAiClient(
                RestClient.builder(), new ObjectMapper(), "http://localhost:1", "test-api-key",
                "gpt-4.1-mini", "gpt-image-1", "gpt-image-1", 15_000L);

        assertThatThrownBy(() -> client.inferFromImage(new byte[0], "x.jpg", null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("No se recibio imagen");
    }
}
