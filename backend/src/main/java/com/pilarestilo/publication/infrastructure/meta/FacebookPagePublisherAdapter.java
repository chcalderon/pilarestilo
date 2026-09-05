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
public class FacebookPagePublisherAdapter implements SocialPlatformPublisher {

    private final RestClient.Builder restClientBuilder;
    private final MetaPublishingConfigResolver configResolver;
    private final ObjectMapper objectMapper;

    public FacebookPagePublisherAdapter(RestClient.Builder restClientBuilder,
                                        MetaPublishingConfigResolver configResolver,
                                        ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.configResolver = configResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public PublicationDispatcher.DispatchResult publish(PublicationDispatchPayload payload) {
        MetaPublishingConfigResolver.EffectiveConfig config = configResolver.resolve();
        if (config.facebookPageId() == null || config.facebookPageAccessToken() == null) {
            return failed("Facebook credentials are not configured");
        }

        RestClient client = restClientBuilder.baseUrl(config.facebookBaseUrl()).build();
        try {
            // The Graph API returns JSON with Content-Type: text/javascript, which no JSON
            // message converter is registered for — read the raw string and parse it directly.
            String raw = client.post()
                    .uri("/{pageId}/photos?url={imageUrl}&caption={caption}&access_token={token}",
                            config.facebookPageId(), payload.mediaUrl(), payload.fullCaptionText(),
                            config.facebookPageAccessToken())
                    .retrieve()
                    .body(String.class);
            JsonNode response = raw == null || raw.isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(raw);

            String postId = response.hasNonNull("post_id") ? response.get("post_id").asString() : null;
            String remotePostId = postId != null ? postId
                    : (response.hasNonNull("id") ? response.get("id").asString() : null);
            String permalink = postId == null ? null : "https://www.facebook.com/" + postId;

            return new PublicationDispatcher.DispatchResult(
                    UUID.randomUUID().toString(), null, PublicationAttemptStatus.SUCCEEDED,
                    remotePostId, null, null, permalink);
        } catch (RuntimeException ex) {
            return failed(ex.getMessage());
        }
    }

    private PublicationDispatcher.DispatchResult failed(String message) {
        return new PublicationDispatcher.DispatchResult(
                UUID.randomUUID().toString(), null, PublicationAttemptStatus.FAILED, null,
                "FACEBOOK_PUBLISH_ERROR", message, null);
    }
}
