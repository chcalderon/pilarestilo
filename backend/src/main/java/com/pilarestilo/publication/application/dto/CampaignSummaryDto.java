package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationPlatform;

import java.time.Instant;
import java.util.Set;

public record CampaignSummaryDto(
        String label,
        Instant firstPostAt,
        Instant lastPostAt,
        int batchCount,
        int totalPosts,
        int published,
        int failed,
        int scheduled,
        Set<PublicationPlatform> platforms,
        MetricsTotals totals,
        int postsWithError
) {
    public record MetricsTotals(long impressions, long reach, long likes, long comments,
                                long shares, long saved) {}
}
