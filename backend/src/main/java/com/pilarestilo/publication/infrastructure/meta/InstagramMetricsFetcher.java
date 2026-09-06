package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import com.pilarestilo.publication.domain.model.PostMetrics;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class InstagramMetricsFetcher {

    // Feed image/carousel metrics available on Graph v23.0. If Graph rejects a name, drop it here —
    // the switch below already leaves absent metrics null. "impressions" is derived from "views"
    // when present (impressions was deprecated for media created after 2024-07).
    private static final String INSIGHT_METRICS = "reach,saved,shares,total_interactions,views";

    private final RestClient.Builder restClientBuilder;
    private final MetaPublishingConfigResolver configResolver;
    private final ObjectMapper objectMapper;

    InstagramMetricsFetcher(RestClient.Builder restClientBuilder,
                            MetaPublishingConfigResolver configResolver,
                            ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.configResolver = configResolver;
        this.objectMapper = objectMapper;
    }

    PublicationMetricsFetcher.Result fetch(String externalPostId) {
        MetaPublishingConfigResolver.EffectiveConfig config = configResolver.resolve();
        if (config.instagramUserId() == null || config.instagramAccessToken() == null) {
            return PublicationMetricsFetcher.Result.failed("Instagram credentials are not configured");
        }
        RestClient client = restClientBuilder.baseUrl(config.instagramBaseUrl()).build();
        String token = config.instagramAccessToken();
        try {
            JsonNode fields = getJson(client,
                    "/{mediaId}?fields=like_count,comments_count&access_token={token}", externalPostId, token);
            Long likes = asLong(fields.get("like_count"));
            Long comments = asLong(fields.get("comments_count"));

            JsonNode insights = getJson(client,
                    "/{mediaId}/insights?metric={metrics}&access_token={token}",
                    externalPostId, INSIGHT_METRICS, token);
            Long reach = null;
            Long saved = null;
            Long shares = null;
            Long impressions = null;
            if (insights.hasNonNull("data")) {
                for (JsonNode metric : insights.get("data")) {
                    String name = metric.hasNonNull("name") ? metric.get("name").asString() : "";
                    Long value = firstValue(metric);
                    switch (name) {
                        case "reach" -> reach = value;
                        case "saved" -> saved = value;
                        case "shares" -> shares = value;
                        case "views" -> impressions = value;
                        default -> { /* total_interactions and anything new: ignored */ }
                    }
                }
            }
            return PublicationMetricsFetcher.Result.ok(
                    new PostMetrics(impressions, reach, likes, comments, shares, saved));
        } catch (RuntimeException ex) {
            return PublicationMetricsFetcher.Result.failed(ex.getMessage());
        }
    }

    private Long firstValue(JsonNode metric) {
        JsonNode values = metric.get("values");
        if (values == null || !values.isArray() || values.isEmpty()) {
            return null;
        }
        return asLong(values.get(0).get("value"));
    }

    private Long asLong(JsonNode node) {
        return node == null || node.isNull() ? null : node.asLong();
    }

    private JsonNode getJson(RestClient client, String uri, Object... uriVars) {
        String raw = client.get().uri(uri, uriVars).retrieve().body(String.class);
        return raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    }
}
