package com.pilarestilo.publication.infrastructure.web.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreatePublicationRequest(
        UUID productId,
        @NotNull String sourceType,
        UUID sourceId,
        @NotNull String platform,
        @NotNull String channelType,
        String locale,
        String campaignLabel,
        String caption,
        List<String> hashtags,
        Boolean approvalRequired,
        Instant scheduledAt,
        String idempotencyKey,
        @Valid List<MediaBundleInput> mediaBundles
) {
    public record MediaBundleInput(
            @NotNull String bundleType,
            @NotBlank String primaryAssetUrl,
            Map<String, Object> assetManifest
    ) {}
}
