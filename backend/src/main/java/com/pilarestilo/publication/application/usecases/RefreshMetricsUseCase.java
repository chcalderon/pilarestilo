package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.publication.application.MetricsUpsertService;
import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Refreshes publication_metrics for a set of PUBLISHED posts. Not @Transactional over the loop:
 * each upsert is its own @Transactional call on MetricsUpsertService (same reasoning as
 * PublishProductsBatchUseCase).
 */
@Component
public class RefreshMetricsUseCase {

    private final PublicationJpaRepository publicationRepository;
    private final PublicationMetricsFetcher fetcher;
    private final MetricsUpsertService upsertService;
    private final Clock clock;

    @Autowired
    public RefreshMetricsUseCase(PublicationJpaRepository publicationRepository,
                                 PublicationMetricsFetcher fetcher,
                                 MetricsUpsertService upsertService) {
        this(publicationRepository, fetcher, upsertService, Clock.systemUTC());
    }

    RefreshMetricsUseCase(PublicationJpaRepository publicationRepository,
                          PublicationMetricsFetcher fetcher,
                          MetricsUpsertService upsertService,
                          Clock clock) {
        this.publicationRepository = publicationRepository;
        this.fetcher = fetcher;
        this.upsertService = upsertService;
        this.clock = clock;
    }

    public MetricsRefreshResult execute(MetricsRefreshScope scope) {
        List<PublicationEntity> targets = switch (scope) {
            case MetricsRefreshScope.Campaign c ->
                    publicationRepository.findPublishedWithPostIdByCampaignLabel(c.label());
            case MetricsRefreshScope.RecentDays r ->
                    publicationRepository.findPublishedWithPostIdSince(
                            Instant.now(clock).minus(Duration.ofDays(r.days())));
        };
        int refreshed = 0;
        int failed = 0;
        for (PublicationEntity p : targets) {
            PublicationMetricsFetcher.Result result = fetcher.fetch(p.getPlatform(), p.getExternalPostId());
            upsertService.upsert(p.getId(), result, Instant.now(clock));
            if (result.metrics().isPresent()) {
                refreshed++;
            } else {
                failed++;
            }
        }
        return new MetricsRefreshResult(refreshed, failed);
    }

    public record MetricsRefreshResult(int refreshed, int failed) {}
}
