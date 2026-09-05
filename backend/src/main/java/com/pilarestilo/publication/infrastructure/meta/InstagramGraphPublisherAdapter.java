package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
public class InstagramGraphPublisherAdapter implements SocialPlatformPublisher {

    private final RestClient.Builder restClientBuilder;
    private final MetaPublishingConfigResolver configResolver;
    private final ObjectMapper objectMapper;

    public InstagramGraphPublisherAdapter(RestClient.Builder restClientBuilder,
                                          MetaPublishingConfigResolver configResolver,
                                          ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.configResolver = configResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public PublicationDispatcher.DispatchResult publish(PublicationDispatchPayload payload) {
        MetaPublishingConfigResolver.EffectiveConfig config = configResolver.resolve();
        if (config.instagramUserId() == null || config.instagramAccessToken() == null) {
            return failed("Instagram credentials are not configured");
        }

        RestClient client = restClientBuilder.baseUrl(config.instagramBaseUrl()).build();
        try {
            JsonNode created = postJson(client,
                    "/{userId}/media?image_url={imageUrl}&caption={caption}&access_token={token}",
                    config.instagramUserId(), payload.mediaUrl(), payload.fullCaptionText(), config.instagramAccessToken());
            String creationId = created.hasNonNull("id") ? created.get("id").asString() : null;

            JsonNode published = postJson(client,
                    "/{userId}/media_publish?creation_id={creationId}&access_token={token}",
                    config.instagramUserId(), creationId, config.instagramAccessToken());
            String remotePostId = published.hasNonNull("id") ? published.get("id").asString() : null;

            String permalink = null;
            try {
                String raw = client.get()
                        .uri("/{mediaId}?fields=permalink&access_token={token}", remotePostId, config.instagramAccessToken())
                        .retrieve()
                        .body(String.class);
                JsonNode node = raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
                permalink = node.hasNonNull("permalink") ? node.get("permalink").asString() : null;
            } catch (RuntimeException permalinkError) {
                // The post is already live; a permalink lookup failure must not flip it to failed.
            }

            return new PublicationDispatcher.DispatchResult(
                    UUID.randomUUID().toString(), null, PublicationAttemptStatus.SUCCEEDED, remotePostId, null, null, permalink);
        } catch (RuntimeException ex) {
            return failed(ex.getMessage());
        }
    }

    /** Graph API returns JSON as Content-Type: text/javascript — read the raw string and parse it. */
    private JsonNode postJson(RestClient client, String uri, Object... uriVars) {
        String raw = client.post().uri(uri, uriVars).retrieve().body(String.class);
        return raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    }

    private PublicationDispatcher.DispatchResult failed(String message) {
        return new PublicationDispatcher.DispatchResult(
                UUID.randomUUID().toString(), null, PublicationAttemptStatus.FAILED, null,
                "INSTAGRAM_PUBLISH_ERROR", message, null);
    }
}
