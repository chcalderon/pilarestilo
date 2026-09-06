package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            if (payload.mediaUrls().size() == 1) {
                return publishSinglePhoto(client, config, payload.mediaUrls().get(0), payload.fullCaptionText());
            }
            return publishCarousel(client, config, payload.mediaUrls(), payload.fullCaptionText());
        } catch (RuntimeException ex) {
            return failed(ex.getMessage());
        }
    }

    private PublicationDispatcher.DispatchResult publishSinglePhoto(RestClient client,
                                                                   MetaPublishingConfigResolver.EffectiveConfig config,
                                                                   String imageUrl, String caption) {
        JsonNode response = parse(client.post()
                .uri("/{pageId}/photos?url={imageUrl}&caption={caption}&access_token={token}",
                        config.facebookPageId(), imageUrl, caption, config.facebookPageAccessToken())
                .retrieve().body(String.class));

        String postId = response.hasNonNull("post_id") ? response.get("post_id").asString() : null;
        String remotePostId = postId != null ? postId
                : (response.hasNonNull("id") ? response.get("id").asString() : null);
        String permalink = postId == null ? null : "https://www.facebook.com/" + postId;

        return new PublicationDispatcher.DispatchResult(
                UUID.randomUUID().toString(), null, PublicationAttemptStatus.SUCCEEDED,
                remotePostId, null, null, permalink);
    }

    private PublicationDispatcher.DispatchResult publishCarousel(RestClient client,
                                                                MetaPublishingConfigResolver.EffectiveConfig config,
                                                                List<String> mediaUrls, String caption) {
        List<String> photoIds = new ArrayList<>();
        for (String url : mediaUrls) {
            JsonNode photo = parse(client.post()
                    .uri("/{pageId}/photos?url={url}&published=false&access_token={token}",
                            config.facebookPageId(), url, config.facebookPageAccessToken())
                    .retrieve().body(String.class));
            String photoId = photo.hasNonNull("id") ? photo.get("id").asString() : null;
            if (photoId == null) {
                throw new IllegalStateException("Facebook did not return an unpublished photo id");
            }
            photoIds.add(photoId);
        }
        String attachedMedia = objectMapper.writeValueAsString(
                photoIds.stream().map(id -> Map.of("media_fbid", id)).toList());
        JsonNode feed = parse(client.post()
                .uri("/{pageId}/feed?message={message}&attached_media={attachedMedia}&access_token={token}",
                        config.facebookPageId(), caption, attachedMedia, config.facebookPageAccessToken())
                .retrieve().body(String.class));
        String postId = feed.hasNonNull("id") ? feed.get("id").asString() : null;
        String permalink = postId == null ? null : "https://www.facebook.com/" + postId;
        return new PublicationDispatcher.DispatchResult(
                UUID.randomUUID().toString(), null, PublicationAttemptStatus.SUCCEEDED,
                postId, null, null, permalink);
    }

    /** The Graph API returns JSON as Content-Type: text/javascript — read the raw string and parse it. */
    private JsonNode parse(String raw) {
        return raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    }

    private PublicationDispatcher.DispatchResult failed(String message) {
        return new PublicationDispatcher.DispatchResult(
                UUID.randomUUID().toString(), null, PublicationAttemptStatus.FAILED, null,
                "FACEBOOK_PUBLISH_ERROR", message, null);
    }
}
