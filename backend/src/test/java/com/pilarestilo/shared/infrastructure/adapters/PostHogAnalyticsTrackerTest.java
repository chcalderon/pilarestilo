package com.pilarestilo.shared.infrastructure.adapters;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PostHogAnalyticsTrackerTest {

    private HttpServer server;
    private final BlockingQueue<String> received = new ArrayBlockingQueue<>(4);

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private PostHogAnalyticsTracker trackerFor(String capturePath) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(capturePath, this::capture);
        server.start();
        String host = "http://localhost:" + server.getAddress().getPort();
        return new PostHogAnalyticsTracker(RestClient.builder(), host, "phc_test_key", capturePath, 2000L);
    }

    private void capture(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received.offer(body);
        byte[] ok = "{\"status\":1}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, ok.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(ok);
        }
    }

    @Test
    void posts_the_event_keyed_to_the_distinct_id_with_the_project_key_and_properties() throws Exception {
        PostHogAnalyticsTracker tracker = trackerFor("/i/v0/e/");

        tracker.track("order_paid", "cust-1", Map.of("total", 29990, "currency", "CLP"));

        String body = received.poll(3, TimeUnit.SECONDS);
        assertThat(body).isNotNull();
        JsonNode json = new ObjectMapper().readTree(body);
        assertThat(json.get("api_key").asString()).isEqualTo("phc_test_key");
        assertThat(json.get("event").asString()).isEqualTo("order_paid");
        assertThat(json.get("distinct_id").asString()).isEqualTo("cust-1");
        assertThat(json.get("timestamp").asString()).isNotBlank();
        JsonNode props = json.get("properties");
        assertThat(props.get("total").asInt()).isEqualTo(29990);
        assertThat(props.get("currency").asString()).isEqualTo("CLP");
        assertThat(props.get("$lib").asString()).isEqualTo("pe-backend");
    }

    @Test
    void a_blank_event_or_distinct_id_sends_nothing() throws Exception {
        PostHogAnalyticsTracker tracker = trackerFor("/i/v0/e/");

        tracker.track("", "cust-1", Map.of());
        tracker.track("order_paid", "  ", Map.of());

        assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void a_capture_failure_is_swallowed() throws Exception {
        PostHogAnalyticsTracker tracker = trackerFor("/i/v0/e/");
        server.stop(0);
        server = null;

        tracker.track("order_created", "cust-1", Map.of());
        // no exception propagates; nothing to assert beyond that the call returns
    }
}
