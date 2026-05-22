package com.pilarestilo.publication.application.commands;

import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationMediaBundleType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationSourceType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreatePublicationCommand(
        UUID productId,
        PublicationSourceType sourceType,
        UUID sourceId,
        PublicationPlatform platform,
        PublicationChannelType channelType,
        String locale,
        String campaignLabel,
        String caption,
        List<String> hashtags,
        boolean approvalRequired,
        Instant scheduledAt,
        String idempotencyKey,
        List<MediaBundleCommand> mediaBundles
) {
    public record MediaBundleCommand(
            PublicationMediaBundleType bundleType,
            String primaryAssetUrl,
            Map<String, Object> assetManifest
    ) {}
}
