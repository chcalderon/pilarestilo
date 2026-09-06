package com.pilarestilo.publication.application;

import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import com.pilarestilo.publication.domain.model.PostMetrics;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMetricsEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationMetricsJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsUpsertServiceTest {

    @Mock PublicationMetricsJpaRepository repo;

    @Test
    void inserts_a_new_row_on_a_successful_fetch() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        Instant now = Instant.parse("2026-09-06T12:00:00Z");

        new MetricsUpsertService(repo).upsert(id,
                PublicationMetricsFetcher.Result.ok(new PostMetrics(100L, 80L, 45L, 3L, 1L, 12L)), now);

        ArgumentCaptor<PublicationMetricsEntity> captor = ArgumentCaptor.forClass(PublicationMetricsEntity.class);
        verify(repo).save(captor.capture());
        assertEquals(45L, captor.getValue().getLikes());
        assertEquals(now, captor.getValue().getFetchedAt());
        assertNull(captor.getValue().getFetchError());
    }

    @Test
    void a_failed_fetch_keeps_old_values_and_sets_the_error() {
        UUID id = UUID.randomUUID();
        PublicationMetricsEntity existing = new PublicationMetricsEntity();
        existing.setPublicationId(id);
        existing.setLikes(40L);
        when(repo.findById(id)).thenReturn(Optional.of(existing));

        new MetricsUpsertService(repo).upsert(id,
                PublicationMetricsFetcher.Result.failed("403 forbidden"), Instant.now());

        ArgumentCaptor<PublicationMetricsEntity> captor = ArgumentCaptor.forClass(PublicationMetricsEntity.class);
        verify(repo).save(captor.capture());
        assertEquals(40L, captor.getValue().getLikes());
        assertEquals("403 forbidden", captor.getValue().getFetchError());
    }
}
