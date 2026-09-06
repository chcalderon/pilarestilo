package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchDuePublicationsUseCaseTest {

    @Mock PublicationJpaRepository publicationRepository;
    @Mock PublicationService publicationService;

    private final Instant now = Instant.parse("2026-09-06T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private DispatchDuePublicationsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DispatchDuePublicationsUseCase(publicationRepository, publicationService, clock, 360L, 25, 15);
        lenient().when(publicationRepository.findByStatusAndUpdatedAtLessThan(eq(PublicationStatus.PUBLISHING), any()))
                .thenReturn(List.of());
    }

    private PublicationEntity row(PublicationStatus status, Instant scheduledAt) {
        PublicationEntity e = new PublicationEntity();
        e.setId(UUID.randomUUID());
        e.setStatus(status);
        e.setPlatform(PublicationPlatform.INSTAGRAM);
        e.setScheduledAt(scheduledAt);
        return e;
    }

    @Test
    void dispatches_a_due_approved_row() {
        PublicationEntity due = row(PublicationStatus.APPROVED, null);
        when(publicationRepository.findDueForDispatch(eq(now), any(Pageable.class))).thenReturn(List.of(due));

        assertEquals(1, useCase.execute());
        verify(publicationService).dispatchFromWorker(due.getId());
    }

    @Test
    void fails_a_scheduled_row_that_is_way_overdue() {
        PublicationEntity stale = row(PublicationStatus.SCHEDULED, now.minusSeconds(7 * 3600));
        when(publicationRepository.findDueForDispatch(eq(now), any(Pageable.class))).thenReturn(List.of(stale));

        useCase.execute();
        verify(publicationService).markScheduleWindowMissed(stale.getId());
        verify(publicationService, never()).dispatchFromWorker(stale.getId());
    }

    @Test
    void recovers_a_stuck_publishing_row() {
        PublicationEntity stuck = row(PublicationStatus.PUBLISHING, null);
        when(publicationRepository.findByStatusAndUpdatedAtLessThan(eq(PublicationStatus.PUBLISHING), any()))
                .thenReturn(List.of(stuck));
        when(publicationRepository.findDueForDispatch(eq(now), any(Pageable.class))).thenReturn(List.of());

        useCase.execute();
        verify(publicationService).markDispatchInterrupted(stuck.getId());
    }

    @Test
    void one_row_throwing_does_not_stop_the_pass() {
        PublicationEntity a = row(PublicationStatus.APPROVED, null);
        PublicationEntity b = row(PublicationStatus.APPROVED, null);
        when(publicationRepository.findDueForDispatch(eq(now), any(Pageable.class))).thenReturn(List.of(a, b));
        when(publicationService.dispatchFromWorker(a.getId())).thenThrow(new DomainException("boom"));

        assertEquals(1, useCase.execute());
        verify(publicationService).dispatchFromWorker(b.getId());
    }
}
