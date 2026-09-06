package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class InstagramGraphPublisherAdapter implements SocialPlatformPublisher {

    private final RestClient.Builder restClientBuilder;
    private final MetaPublishingConfigResolver configResolver;
    private final ObjectMapper objectMapper;
    private final long containerPollIntervalMs;
    private final int containerPollMaxAttempts;

    @Autowired
    public InstagramGraphPublisherAdapter(
            RestClient.Builder restClientBuilder,
            MetaPublishingConfigResolver configResolver,
            ObjectMapper objectMapper,
            @Value("${app.social-publishing.meta.instagram.container-poll-interval-ms:3000}") long containerPollIntervalMs,
            @Value("${app.social-publishing.meta.instagram.container-poll-max-attempts:10}") int containerPollMaxAttempts) {
        this.restClientBuilder = restClientBuilder;
        this.configResolver = configResolver;
        this.objectMapper = objectMapper;
        this.containerPollIntervalMs = containerPollIntervalMs;
        this.containerPollMaxAttempts = containerPollMaxAttempts;
    }

    // Test seam: skips the real waits between status polls.
    InstagramGraphPublisherAdapter(RestClient.Builder restClientBuilder,
                                   MetaPublishingConfigResolver configResolver,
                                   ObjectMapper objectMapper) {
        this(restClientBuilder, configResolver, objectMapper, 0L, 10);
    }

    @Override
    public PublicationDispatcher.DispatchResult publish(PublicationDispatchPayload payload) {
        MetaPublishingConfigResolver.EffectiveConfig config = configResolver.resolve();
        if (config.instagramUserId() == null || config.instagramAccessToken() == null) {
            return failed("Instagram credentials are not configured");
        }

        RestClient client = restClientBuilder.baseUrl(config.instagramBaseUrl()).build();
        try {
            String creationId = buildPublishableContainer(client, config, payload.mediaUrls(), payload.fullCaptionText());

            JsonNode published = postJson(client,
                    "/{userId}/media_publish?creation_id={creationId}&access_token={token}",
                    config.instagramUserId(), creationId, config.instagramAccessToken());
            String remotePostId = published.hasNonNull("id") ? published.get("id").asString() : null;

            String permalink = fetchPermalink(client, remotePostId, config.instagramAccessToken());

            return new PublicationDispatcher.DispatchResult(
                    UUID.randomUUID().toString(), null, PublicationAttemptStatus.SUCCEEDED, remotePostId, null, null, permalink);
        } catch (RuntimeException ex) {
            return failed(ex.getMessage());
        }
    }

    /**
     * Builds the container to publish. One image -> a single media container with the caption.
     * Two or more -> N child containers ({@code is_carousel_item=true}) then a CAROUSEL parent
     * carrying the caption. Every container is polled to FINISHED before it is used.
     */
    private String buildPublishableContainer(RestClient client, MetaPublishingConfigResolver.EffectiveConfig config,
                                             List<String> mediaUrls, String caption) {
        if (mediaUrls.size() == 1) {
            JsonNode created = postJson(client,
                    "/{userId}/media?image_url={imageUrl}&caption={caption}&access_token={token}",
                    config.instagramUserId(), mediaUrls.get(0), caption, config.instagramAccessToken());
            String id = created.hasNonNull("id") ? created.get("id").asString() : null;
            if (id == null) {
                throw new IllegalStateException("Instagram did not return a media container id");
            }
            awaitContainerReady(client, id, config.instagramAccessToken());
            return id;
        }
        List<String> childIds = new ArrayList<>();
        for (String url : mediaUrls) {
            JsonNode child = postJson(client,
                    "/{userId}/media?image_url={imageUrl}&is_carousel_item=true&access_token={token}",
                    config.instagramUserId(), url, config.instagramAccessToken());
            String childId = child.hasNonNull("id") ? child.get("id").asString() : null;
            if (childId == null) {
                throw new IllegalStateException("Instagram did not return a carousel child container id");
            }
            awaitContainerReady(client, childId, config.instagramAccessToken());
            childIds.add(childId);
        }
        JsonNode parent = postJson(client,
                "/{userId}/media?media_type=CAROUSEL&caption={caption}&children={children}&access_token={token}",
                config.instagramUserId(), caption, String.join(",", childIds), config.instagramAccessToken());
        String parentId = parent.hasNonNull("id") ? parent.get("id").asString() : null;
        if (parentId == null) {
            throw new IllegalStateException("Instagram did not return a carousel parent container id");
        }
        awaitContainerReady(client, parentId, config.instagramAccessToken());
        return parentId;
    }

    /**
     * A freshly created media container is processed asynchronously; calling media_publish before
     * it reaches FINISHED fails with code 9007 "Media ID is not available". Poll status_code until
     * the container is ready, or give up with a message the retry path can act on.
     */
    private void awaitContainerReady(RestClient client, String creationId, String token) {
        for (int attempt = 0; attempt < containerPollMaxAttempts; attempt++) {
            JsonNode status = getJson(client, "/{creationId}?fields=status_code&access_token={token}", creationId, token);
            String code = status.hasNonNull("status_code") ? status.get("status_code").asString() : "";
            switch (code) {
                case "FINISHED", "PUBLISHED" -> {
                    return;
                }
                case "ERROR", "EXPIRED" -> throw new IllegalStateException(
                        "Instagram media container " + code.toLowerCase() + " before it could be published");
                default -> sleepBetweenPolls();
            }
        }
        throw new IllegalStateException(
                "Instagram media container was not ready after " + containerPollMaxAttempts + " checks");
    }

    private void sleepBetweenPolls() {
        if (containerPollIntervalMs <= 0) {
            return;
        }
        try {
            Thread.sleep(containerPollIntervalMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the Instagram media container");
        }
    }

    private String fetchPermalink(RestClient client, String remotePostId, String token) {
        try {
            JsonNode node = getJson(client, "/{mediaId}?fields=permalink&access_token={token}", remotePostId, token);
            return node.hasNonNull("permalink") ? node.get("permalink").asString() : null;
        } catch (RuntimeException permalinkError) {
            // The post is already live; a permalink lookup failure must not flip it to failed.
            return null;
        }
    }

    /** Graph API returns JSON as Content-Type: text/javascript — read the raw string and parse it. */
    private JsonNode postJson(RestClient client, String uri, Object... uriVars) {
        return parse(client.post().uri(uri, uriVars).retrieve().body(String.class));
    }

    private JsonNode getJson(RestClient client, String uri, Object... uriVars) {
        return parse(client.get().uri(uri, uriVars).retrieve().body(String.class));
    }

    private JsonNode parse(String raw) {
        return raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    }

    private PublicationDispatcher.DispatchResult failed(String message) {
        return new PublicationDispatcher.DispatchResult(
                UUID.randomUUID().toString(), null, PublicationAttemptStatus.FAILED, null,
                "INSTAGRAM_PUBLISH_ERROR", message, null);
    }
}
