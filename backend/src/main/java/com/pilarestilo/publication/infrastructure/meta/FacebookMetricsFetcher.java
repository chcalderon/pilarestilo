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

    PublicationMetricsFetcher.Result fetch(String externalPostId) {
        MetaPublishingConfigResolver.EffectiveConfig config = configResolver.resolve();
        if (config.facebookPageId() == null || config.facebookPageAccessToken() == null) {
            return PublicationMetricsFetcher.Result.failed("Facebook credentials are not configured");
        }
        RestClient client = restClientBuilder.baseUrl(config.facebookBaseUrl()).build();
        String token = config.facebookPageAccessToken();

        Long likes;
        Long comments;
        Long shares;
        try {
            JsonNode summary = getJson(client,
                    "/{postId}?fields=likes.summary(true),comments.summary(true),shares&access_token={token}",
                    externalPostId, token);
            likes = summaryCount(summary.get("likes"));
            comments = summaryCount(summary.get("comments"));
            shares = summary.hasNonNull("shares") && summary.get("shares").hasNonNull("count")
                    ? summary.get("shares").get("count").asLong() : null;
        } catch (RuntimeException ex) {
            return PublicationMetricsFetcher.Result.failed(ex.getMessage());
        }

        Long impressions = null;
        Long reach = null;
        try {
            JsonNode insights = getJson(client,
                    "/{postId}/insights?metric=post_impressions,post_impressions_unique&access_token={token}",
                    externalPostId, token);
            if (insights.hasNonNull("data")) {
                for (JsonNode metric : insights.get("data")) {
                    String name = metric.hasNonNull("name") ? metric.get("name").asString() : "";
                    Long value = firstValue(metric);
                    if ("post_impressions".equals(name)) {
                        impressions = value;
                    } else if ("post_impressions_unique".equals(name)) {
                        reach = value;
                    }
                }
            }
        } catch (RuntimeException insightsError) {
            // read_insights may not be granted — keep the like/comment/share counts.
        }

        return PublicationMetricsFetcher.Result.ok(
                new PostMetrics(impressions, reach, likes, comments, shares, null));
    }

    private Long summaryCount(JsonNode node) {
        if (node == null || !node.hasNonNull("summary") || !node.get("summary").hasNonNull("total_count")) {
            return null;
        }
        return node.get("summary").get("total_count").asLong();
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
