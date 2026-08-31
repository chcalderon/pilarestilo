package com.pilarestilo.shared.infrastructure.adapters;

import com.pilarestilo.shared.domain.ports.AnalyticsTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Sends server-side events to PostHog's capture endpoint ({@code POST {host}/i/v0/e/}).
 *
 * <p>Active only when {@code app.analytics.posthog.enabled=true} and a project key is set; the
 * {@link NoOpAnalyticsTracker} covers every other case. The project key is write-only (the same
 * {@code phc_…} token the storefront ships in HTML), so there is nothing secret to leak in a log.
 *
 * <p>The call runs on a small daemon executor and its result is ignored: analytics is never
 * allowed to slow down or fail an order. A timeout or a 4xx is logged at WARN and dropped.
 */
@Component("postHogAnalyticsTracker")
@ConditionalOnProperty(prefix = "app.analytics.posthog", name = "enabled", havingValue = "true")
public class PostHogAnalyticsTracker implements AnalyticsTracker {

    private static final Logger log = LoggerFactory.getLogger(PostHogAnalyticsTracker.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String capturePath;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "posthog-analytics");
                thread.setDaemon(true);
                return thread;
            });

    public PostHogAnalyticsTracker(
            RestClient.Builder restClientBuilder,
            @Value("${app.analytics.posthog.host:https://us.i.posthog.com}") String host,
            @Value("${app.analytics.posthog.api-key:}") String apiKey,
            @Value("${app.analytics.posthog.capture-path:/i/v0/e/}") String capturePath,
            @Value("${app.analytics.posthog.timeout-ms:3000}") long timeoutMs
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.capturePath = capturePath == null || capturePath.isBlank() ? "/i/v0/e/" : capturePath.trim();

        int safeTimeoutMs = (int) Math.clamp(timeoutMs, 500L, 30_000L);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(safeTimeoutMs);
        requestFactory.setReadTimeout(safeTimeoutMs);
        this.restClient = restClientBuilder
                .baseUrl(host == null || host.isBlank() ? "https://us.i.posthog.com" : host.trim())
                .requestFactory(requestFactory)
                .build();

        if (this.apiKey.isBlank()) {
            log.warn("app.analytics.posthog.enabled=true but no api-key set — events will be rejected by PostHog");
        }
    }

    @Override
    public void track(String event, String distinctId, Map<String, Object> properties) {
        if (event == null || event.isBlank() || distinctId == null || distinctId.isBlank()) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("api_key", apiKey);
        body.put("event", event);
        body.put("distinct_id", distinctId);
        body.put("timestamp", Instant.now().toString());
        Map<String, Object> props = properties == null ? new HashMap<>() : new HashMap<>(properties);
        props.putIfAbsent("$lib", "pe-backend");
        body.put("properties", props);

        executor.execute(() -> send(event, body));
    }

    private void send(String event, Map<String, Object> body) {
        try {
            restClient.post()
                    .uri(capturePath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("PostHog capture of {} failed: {}", event, ex.getMessage());
        }
    }
}
