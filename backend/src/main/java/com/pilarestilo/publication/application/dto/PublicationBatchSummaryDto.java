package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationPlatform;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record PublicationBatchSummaryDto(
        UUID batchId,
        String campaignLabel,
        Instant createdAt,
        Set<PublicationPlatform> platforms,
        int total,
        int published,
        int failed,
        int scheduled,
        int pending,
        Instant scheduledAt
) {}
