package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.domain.model.PostMetrics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CampaignDetailDto(
        String label,
        Instant firstPostAt,
        Instant lastPostAt,
        List<PostRow> posts
) {
    public record PostRow(
            UUID publicationId,
            UUID productId,
            String productName,
            String thumbnailUrl,
            PublicationPlatform platform,
            PublicationStatus status,
            String externalPermalink,
            PostMetrics metrics,
            String fetchError,
            Instant fetchedAt
    ) {}
}
