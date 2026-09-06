package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicationBatchDetailDto(
        UUID batchId,
        String campaignLabel,
        String captionTemplate,
        List<String> hashtags,
        Instant createdAt,
        List<UUID> productIds,
        List<Row> rows,
        Instant scheduledAt
) {
    public record Row(
            UUID publicationId,
            UUID productId,
            String productName,
            String thumbnailUrl,
            PublicationPlatform platform,
            PublicationStatus status,
            String externalPermalink,
            String lastErrorCode,
            String lastErrorMessage,
            List<String> imageUrls
    ) {}
}
