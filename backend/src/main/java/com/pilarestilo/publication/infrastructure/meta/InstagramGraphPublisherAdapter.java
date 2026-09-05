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
public class InstagramGraphPublisherAdapter implements SocialPlatformPublisher {

    private final RestClient.Builder restClientBuilder;
    private final MetaPublishingConfigResolver configResolver;

    public InstagramGraphPublisherAdapter(RestClient.Builder restClientBuilder,
                                          MetaPublishingConfigResolver configResolver) {
        this.restClientBuilder = restClientBuilder;
        this.configResolver = configResolver;
    }

    @Override
    public PublicationDispatcher.DispatchResult publish(PublicationDispatchPayload payload) {
        MetaPublishingConfigResolver.EffectiveConfig config = configResolver.resolve();
        if (config.instagramUserId() == null || config.instagramAccessToken() == null) {
            return failed("Instagram credentials are not configured");
        }

        RestClient client = restClientBuilder.baseUrl(config.instagramBaseUrl()).build();
        try {
            Map<String, Object> created = client.post()
                    .uri("/{userId}/media?image_url={imageUrl}&caption={caption}&access_token={token}",
                            config.instagramUserId(), payload.mediaUrl(), payload.fullCaptionText(), config.instagramAccessToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            String creationId = String.valueOf(created.get("id"));

            Map<String, Object> published = client.post()
                    .uri("/{userId}/media_publish?creation_id={creationId}&access_token={token}",
                            config.instagramUserId(), creationId, config.instagramAccessToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            String remotePostId = String.valueOf(published.get("id"));

            return new PublicationDispatcher.DispatchResult(
                    UUID.randomUUID().toString(), null, PublicationAttemptStatus.SUCCEEDED, remotePostId, null, null);
        } catch (RestClientException ex) {
            return failed(ex.getMessage());
        }
    }

    private PublicationDispatcher.DispatchResult failed(String message) {
        return new PublicationDispatcher.DispatchResult(
                UUID.randomUUID().toString(), null, PublicationAttemptStatus.FAILED, null,
                "INSTAGRAM_PUBLISH_ERROR", message);
    }
}
