package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationMediaBundleType;
import com.pilarestilo.publication.domain.enums.PublicationMediaRenderStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PublicationMediaBundleDto(
        UUID id,
        PublicationMediaBundleType bundleType,
        Map<String, Object> assetManifest,
        String primaryAssetUrl,
        PublicationMediaRenderStatus renderStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
