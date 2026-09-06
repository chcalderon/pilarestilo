package com.pilarestilo.publication.application;

import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import com.pilarestilo.publication.domain.model.PostMetrics;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMetricsEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationMetricsJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Upserts one publication_metrics row. @Transactional per call: RefreshMetricsUseCase loops over
 * this without an outer transaction so one bad write does not roll back the others.
 */
@Service
public class MetricsUpsertService {

    private final PublicationMetricsJpaRepository repository;

    public MetricsUpsertService(PublicationMetricsJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void upsert(UUID publicationId, PublicationMetricsFetcher.Result result, Instant now) {
        PublicationMetricsEntity e = repository.findById(publicationId).orElseGet(() -> {
            PublicationMetricsEntity n = new PublicationMetricsEntity();
            n.setPublicationId(publicationId);
            return n;
        });
        e.setFetchedAt(now);
        var metrics = result.metrics();
        if (metrics.isPresent()) {
            PostMetrics m = metrics.get();
            e.setImpressions(m.impressions());
            e.setReach(m.reach());
            e.setLikes(m.likes());
            e.setComments(m.comments());
            e.setShares(m.shares());
            e.setSaved(m.saved());
            e.setFetchError(null);
        } else {
            e.setFetchError(result.error());
        }
        repository.save(e);
    }
}
