package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PublicationDispatchWebhookPayload(
        String eventType,
        UUID publicationId,
        String idempotencyKey,
        PublicationPlatform platform,
        PublicationChannelType channelType,
        String locale,
        Instant scheduledAt,
        PublicationDispatchContentDto content,
        PublicationDispatchMediaBundleDto mediaBundle,
        Map<String, Object> sourceSnapshot
) {
    public record PublicationDispatchContentDto(
            String caption,
            List<String> hashtags
    ) {}

    public record PublicationDispatchMediaBundleDto(
            String bundleType,
            String primaryAssetUrl,
            Map<String, Object> assetManifest
    ) {}
}
