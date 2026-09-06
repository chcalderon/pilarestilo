package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class MetaDirectPublicationDispatcher implements PublicationDispatcher {

    private final InstagramGraphPublisherAdapter instagram;
    private final FacebookPagePublisherAdapter facebook;
    private final MetaPublishingConfigResolver configResolver;

    public MetaDirectPublicationDispatcher(InstagramGraphPublisherAdapter instagram,
                                           FacebookPagePublisherAdapter facebook,
                                           MetaPublishingConfigResolver configResolver) {
        this.instagram = instagram;
        this.facebook = facebook;
        this.configResolver = configResolver;
    }

    @Override
    public DispatchResult dispatch(UUID publicationId, String idempotencyKey, PublicationDispatchPayload payload) {
        if (payload.mediaUrls() == null || payload.mediaUrls().isEmpty()) {
            throw new DomainException("Cannot dispatch publication without a media URL");
        }
        String base = configResolver.resolve().publicMediaBaseUrl();
        List<String> absolute = payload.mediaUrls().stream()
                .map(u -> resolveAbsoluteUrl(u, base))
                .toList();
        PublicationDispatchPayload resolvedPayload = new PublicationDispatchPayload(
                payload.productId(), payload.platform(), payload.channelType(),
                payload.caption(), payload.hashtags(), absolute);
        return publisherFor(payload.platform()).publish(resolvedPayload);
    }

    private SocialPlatformPublisher publisherFor(PublicationPlatform platform) {
        return switch (platform) {
            case INSTAGRAM -> instagram;
            case FACEBOOK -> facebook;
        };
    }

    @SuppressWarnings("java:S1075") // the "/" joins URL segments, not a filesystem path
    private String resolveAbsoluteUrl(String mediaUrl, String publicBaseUrl) {
        if (mediaUrl != null && (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://"))) {
            return mediaUrl;
        }
        if (mediaUrl == null || mediaUrl.isBlank()) {
            throw new DomainException("Cannot dispatch publication without a media URL");
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new DomainException(
                    "Cannot resolve absolute media URL: app.social-publishing.meta.public-media-base-url is not configured");
        }
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        String path = mediaUrl.startsWith("/") ? mediaUrl : "/" + mediaUrl;
        return base + path;
    }
}
