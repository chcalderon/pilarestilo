package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationApprovalStatus;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationSourceType;
import com.pilarestilo.publication.domain.enums.PublicationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicationDto(
        UUID id,
        UUID productId,
        PublicationSourceType sourceType,
        UUID sourceId,
        PublicationPlatform platform,
        PublicationChannelType channelType,
        PublicationStatus status,
        PublicationApprovalStatus approvalStatus,
        String caption,
        List<String> hashtags,
        String locale,
        String campaignLabel,
        Instant scheduledAt,
        Instant publishedAt,
        String externalPostId,
        String idempotencyKey,
        int contentVersion,
        int snapshotVersion,
        String lastErrorCode,
        String lastErrorMessage,
        int retryCount,
        UUID createdBy,
        UUID approvedBy,
        Instant createdAt,
        Instant updatedAt,
        List<PublicationMediaBundleDto> mediaBundles,
        List<PublicationAttemptDto> attempts,
        List<PublicationReviewDto> reviews,
        List<PublicationSnapshotDto> snapshots
) {
}
