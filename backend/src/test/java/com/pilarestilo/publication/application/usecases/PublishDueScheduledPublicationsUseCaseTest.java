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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishDueScheduledPublicationsUseCaseTest {

    @Mock PublicationJpaRepository publicationRepository;
    @Mock PublicationService publicationService;

    private final Instant fixedNow = Instant.parse("2026-09-06T12:00:00Z");
    private final Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    private PublishDueScheduledPublicationsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PublishDueScheduledPublicationsUseCase(publicationRepository, publicationService, clock, 360L);
    }

    private PublicationEntity due(Instant scheduledAt) {
        PublicationEntity e = new PublicationEntity();
        e.setId(UUID.randomUUID());
        e.setStatus(PublicationStatus.SCHEDULED);
        e.setPlatform(PublicationPlatform.INSTAGRAM);
        e.setScheduledAt(scheduledAt);
        return e;
    }

    @Test
    void dispatches_an_in_window_row_and_fails_a_stale_one() {
        PublicationEntity fresh = due(fixedNow.minusSeconds(120));
        PublicationEntity stale = due(fixedNow.minusSeconds(7 * 3600));
        when(publicationRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                PublicationStatus.SCHEDULED, fixedNow)).thenReturn(List.of(fresh, stale));

        int handled = useCase.execute();

        assertEquals(2, handled);
        verify(publicationService).dispatch(eq(fresh.getId()), any());
        verify(publicationService).markScheduleWindowMissed(stale.getId());
        verify(publicationService, never()).dispatch(eq(stale.getId()), any());
    }

    @Test
    void one_row_throwing_does_not_stop_the_rest() {
        PublicationEntity a = due(fixedNow.minusSeconds(60));
        PublicationEntity b = due(fixedNow.minusSeconds(60));
        when(publicationRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                PublicationStatus.SCHEDULED, fixedNow)).thenReturn(List.of(a, b));
        when(publicationService.dispatch(eq(a.getId()), any())).thenThrow(new DomainException("boom"));

        int handled = useCase.execute();

        assertEquals(1, handled);
        verify(publicationService).dispatch(eq(b.getId()), any());
    }
}
