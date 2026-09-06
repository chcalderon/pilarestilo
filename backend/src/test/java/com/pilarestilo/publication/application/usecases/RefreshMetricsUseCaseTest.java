package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.publication.application.MetricsUpsertService;
import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.model.PostMetrics;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshMetricsUseCaseTest {

    @Mock PublicationJpaRepository publicationRepository;
    @Mock PublicationMetricsFetcher fetcher;
    @Mock MetricsUpsertService upsertService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC);

    private PublicationEntity published(String postId, PublicationPlatform platform) {
        PublicationEntity p = new PublicationEntity();
        p.setId(UUID.randomUUID());
        p.setExternalPostId(postId);
        p.setPlatform(platform);
        return p;
    }

    @Test
    void campaign_scope_selects_by_label_and_counts_results() {
        var a = published("m-1", PublicationPlatform.INSTAGRAM);
        var b = published("p_1", PublicationPlatform.FACEBOOK);
        when(publicationRepository.findPublishedWithPostIdByCampaignLabel("Verano")).thenReturn(List.of(a, b));
        when(fetcher.fetch(PublicationPlatform.INSTAGRAM, "m-1"))
                .thenReturn(PublicationMetricsFetcher.Result.ok(PostMetrics.empty()));
        when(fetcher.fetch(PublicationPlatform.FACEBOOK, "p_1"))
                .thenReturn(PublicationMetricsFetcher.Result.failed("boom"));

        var result = new RefreshMetricsUseCase(publicationRepository, fetcher, upsertService, clock)
                .execute(new MetricsRefreshScope.Campaign("Verano"));

        assertEquals(1, result.refreshed());
        assertEquals(1, result.failed());
        verify(upsertService).upsert(eq(a.getId()), any(), eq(Instant.parse("2026-09-06T12:00:00Z")));
        verify(upsertService).upsert(eq(b.getId()), any(), any());
    }

    @Test
    void recent_days_scope_selects_by_published_at() {
        when(publicationRepository.findPublishedWithPostIdSince(Instant.parse("2026-08-07T12:00:00Z")))
                .thenReturn(List.of());
        var result = new RefreshMetricsUseCase(publicationRepository, fetcher, upsertService, clock)
                .execute(new MetricsRefreshScope.RecentDays(30));
        assertEquals(0, result.refreshed());
        assertEquals(0, result.failed());
    }
}
