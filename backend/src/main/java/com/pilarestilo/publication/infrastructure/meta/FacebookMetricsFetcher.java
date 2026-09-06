package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import com.pilarestilo.publication.domain.model.PostMetrics;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class FacebookMetricsFetcher {

    private final RestClient.Builder restClientBuilder;
    private final MetaPublishingConfigResolver configResolver;
    private final ObjectMapper objectMapper;

    FacebookMetricsFetcher(RestClient.Builder restClientBuilder,
                           MetaPublishingConfigResolver configResolver,
                           ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.configResolver = configResolver;
        this.objectMapper = objectMapper;
    }

    private static final String SUMMARY = "summary";

    private record Engagement(Long likes, Long comments, Long shares) {}
    private record Reach(Long impressions, Long reach) {}

    PublicationMetricsFetcher.Result fetch(String externalPostId) {
        MetaPublishingConfigResolver.EffectiveConfig config = configResolver.resolve();
        if (config.facebookPageId() == null || config.facebookPageAccessToken() == null) {
            return PublicationMetricsFetcher.Result.failed("Facebook credentials are not configured");
        }
        RestClient client = restClientBuilder.baseUrl(config.facebookBaseUrl()).build();
        String token = config.facebookPageAccessToken();

        Engagement e;
        try {
            e = fetchEngagement(client, externalPostId, token);
        } catch (RuntimeException ex) {
            return PublicationMetricsFetcher.Result.failed(ex.getMessage());
        }

        Reach r = fetchReach(client, externalPostId, token);
        return PublicationMetricsFetcher.Result.ok(
                new PostMetrics(r.impressions(), r.reach(), e.likes(), e.comments(), e.shares(), null));
    }

    private Engagement fetchEngagement(RestClient client, String postId, String token) {
        JsonNode summary = getJson(client,
                "/{postId}?fields=likes.summary(true),comments.summary(true),shares&access_token={token}",
                postId, token);
        JsonNode shares = summary.get("shares");
        Long shareCount = shares != null && shares.hasNonNull("count") ? shares.get("count").asLong() : null;
        return new Engagement(summaryCount(summary.get("likes")), summaryCount(summary.get("comments")), shareCount);
    }

    /** read_insights may not be granted — a failure here keeps the like/comment/share counts. */
    private Reach fetchReach(RestClient client, String postId, String token) {
        Long impressions = null;
        Long reach = null;
        try {
            JsonNode insights = getJson(client,
                    "/{postId}/insights?metric=post_impressions,post_impressions_unique&access_token={token}",
                    postId, token);
            if (insights.hasNonNull("data")) {
                for (JsonNode metric : insights.get("data")) {
                    String name = metric.hasNonNull("name") ? metric.get("name").asString() : "";
                    if ("post_impressions".equals(name)) {
                        impressions = firstValue(metric);
                    } else if ("post_impressions_unique".equals(name)) {
                        reach = firstValue(metric);
                    }
                }
            }
        } catch (RuntimeException _) {
            // insight scope not granted
        }
        return new Reach(impressions, reach);
    }

    private Long summaryCount(JsonNode node) {
        if (node == null || !node.hasNonNull(SUMMARY) || !node.get(SUMMARY).hasNonNull("total_count")) {
            return null;
        }
        return node.get(SUMMARY).get("total_count").asLong();
    }

    private Long firstValue(JsonNode metric) {
        JsonNode values = metric.get("values");
        if (values == null || !values.isArray() || values.isEmpty() || values.get(0).get("value") == null) {
            return null;
        }
        return values.get(0).get("value").asLong();
    }

    private JsonNode getJson(RestClient client, String uri, Object... uriVars) {
        String raw = client.get().uri(uri, uriVars).retrieve().body(String.class);
        return raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    }
}
