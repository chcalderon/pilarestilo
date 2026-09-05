package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

@Component
public class FacebookPagePublisherAdapter implements SocialPlatformPublisher {

    private final RestClient.Builder restClientBuilder;
    private final MetaPublishingConfigResolver configResolver;

    public FacebookPagePublisherAdapter(RestClient.Builder restClientBuilder,
                                        MetaPublishingConfigResolver configResolver) {
        this.restClientBuilder = restClientBuilder;
        this.configResolver = configResolver;
    }

    @Override
    public PublicationDispatcher.DispatchResult publish(PublicationDispatchPayload payload) {
        MetaPublishingConfigResolver.EffectiveConfig config = configResolver.resolve();
        if (config.facebookPageId() == null || config.facebookPageAccessToken() == null) {
            return failed("Facebook credentials are not configured");
        }

        RestClient client = restClientBuilder.baseUrl(config.facebookBaseUrl()).build();
        try {
            Map<String, Object> response = client.post()
                    .uri("/{pageId}/photos?url={imageUrl}&caption={caption}&access_token={token}",
                            config.facebookPageId(), payload.mediaUrl(), payload.fullCaptionText(), config.facebookPageAccessToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            Object postIdRaw = response == null ? null : response.get("post_id");
            String remotePostId = postIdRaw != null ? String.valueOf(postIdRaw)
                    : (response != null && response.get("id") != null ? String.valueOf(response.get("id")) : null);
            String permalink = postIdRaw == null ? null : "https://www.facebook.com/" + postIdRaw;

            return new PublicationDispatcher.DispatchResult(
                    UUID.randomUUID().toString(), null, PublicationAttemptStatus.SUCCEEDED,
                    remotePostId, null, null, permalink);
        } catch (RestClientException ex) {
            return failed(ex.getMessage());
        }
    }

    private PublicationDispatcher.DispatchResult failed(String message) {
        return new PublicationDispatcher.DispatchResult(
                UUID.randomUUID().toString(), null, PublicationAttemptStatus.FAILED, null,
                "FACEBOOK_PUBLISH_ERROR", message, null);
    }
}
