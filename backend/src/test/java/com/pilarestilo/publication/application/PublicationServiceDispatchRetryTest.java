package com.pilarestilo.publication.application;

import tools.jackson.databind.ObjectMapper;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationApprovalStatus;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationSourceType;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.domain.events.PublicationDispatchFailed;
import com.pilarestilo.publication.domain.events.PublicationDispatchScheduledForRetry;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationBatchJpaRepository;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicationServiceDispatchRetryTest {

    @Mock PublicationBatchJpaRepository batchRepo;
    @Mock PublicationJpaRepository pubRepo;
    @Mock ProductRepository productRepo;
    @Mock PublicationDispatcher dispatcher;
    @Mock DomainEventPublisher events;

    PublicationService service;

    @BeforeEach
    void setUp() {
        service = new PublicationService(batchRepo, pubRepo, productRepo, dispatcher, events,
                new ObjectMapper(), new DispatchBackoffPolicy(List.of(2, 10, 30, 120, 360)));
        when(pubRepo.save(any(PublicationEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    private PublicationEntity approvedRow() {
        PublicationEntity e = new PublicationEntity();
        e.setId(UUID.randomUUID());
        e.setStatus(PublicationStatus.APPROVED);
        e.setApprovalStatus(PublicationApprovalStatus.APPROVED);
        e.setPlatform(PublicationPlatform.INSTAGRAM);
        e.setChannelType(PublicationChannelType.FEED_POST);
        e.setSourceType(PublicationSourceType.MANUAL);
        e.setCaption("Copy");
        e.setHashtagsJson("[]");
        e.setLocale("es-CL");
        e.setIdempotencyKey("k-" + e.getId());
        e.setContentVersion(1);
        e.setSnapshotVersion(0);
        e.setRetryCount(0);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(e.getCreatedAt());
        e.setAttempts(new ArrayList<>());
        e.setSnapshots(new ArrayList<>());
        e.setReviews(new ArrayList<>());
        e.setMediaBundles(new ArrayList<>());
        return e;
    }

    private PublicationDispatcher.DispatchResult transientFailure() {
        return new PublicationDispatcher.DispatchResult(null, null,
                PublicationAttemptStatus.FAILED, null, "INSTAGRAM_PUBLISH_ERROR", "429", null, true);
    }

    private PublicationDispatcher.DispatchResult permanentFailure() {
        return new PublicationDispatcher.DispatchResult(null, null,
                PublicationAttemptStatus.FAILED, null, "INSTAGRAM_PUBLISH_ERROR", "bad token", null, false);
    }

    private PublicationDispatcher.DispatchResult success() {
        return new PublicationDispatcher.DispatchResult("req", null,
                PublicationAttemptStatus.SUCCEEDED, "ig-1", null, null, "https://instagram.com/p/x", true);
    }

    @Test
    void transient_failure_on_first_attempt_schedules_a_retry_two_minutes_out() {
        PublicationEntity row = approvedRow();
        when(pubRepo.findById(row.getId())).thenReturn(Optional.of(row));
        when(dispatcher.dispatch(any(), any(), any())).thenReturn(transientFailure());
        Instant before = Instant.now();

        service.dispatch(row.getId(), null);

        assertEquals(PublicationStatus.RETRY_SCHEDULED, row.getStatus());
        assertEquals(1, row.getRetryCount());
        assertNotNull(row.getNextAttemptAt());
        long gapMin = ChronoUnit.MINUTES.between(before, row.getNextAttemptAt());
        assertTrue(gapMin >= 1 && gapMin <= 3, "retry ~2 min out, was " + gapMin);
        verify(events).publish(any(PublicationDispatchScheduledForRetry.class));
    }

    @Test
    void transient_failure_after_the_retry_budget_is_terminal() {
        PublicationEntity row = approvedRow();
        row.setRetryCount(5);
        row.setStatus(PublicationStatus.RETRY_SCHEDULED);
        when(pubRepo.findById(row.getId())).thenReturn(Optional.of(row));
        when(dispatcher.dispatch(any(), any(), any())).thenReturn(transientFailure());

        service.dispatch(row.getId(), null);

        assertEquals(PublicationStatus.FAILED, row.getStatus());
        assertNull(row.getNextAttemptAt());
        verify(events).publish(any(PublicationDispatchFailed.class));
    }

    @Test
    void permanent_failure_fails_immediately_regardless_of_budget() {
        PublicationEntity row = approvedRow();
        when(pubRepo.findById(row.getId())).thenReturn(Optional.of(row));
        when(dispatcher.dispatch(any(), any(), any())).thenReturn(permanentFailure());

        service.dispatch(row.getId(), null);

        assertEquals(PublicationStatus.FAILED, row.getStatus());
        assertEquals(0, row.getRetryCount());
        assertNull(row.getNextAttemptAt());
    }

    @Test
    void success_clears_the_next_attempt() {
        PublicationEntity row = approvedRow();
        when(pubRepo.findById(row.getId())).thenReturn(Optional.of(row));
        when(dispatcher.dispatch(any(), any(), any())).thenReturn(success());

        service.dispatch(row.getId(), null);

        assertEquals(PublicationStatus.PUBLISHED, row.getStatus());
        assertNull(row.getNextAttemptAt());
    }

    @Test
    void manual_retry_resets_the_budget_and_does_not_dispatch_inline() {
        PublicationEntity row = approvedRow();
        row.setStatus(PublicationStatus.FAILED);
        row.setRetryCount(5);
        row.setLastErrorCode("INSTAGRAM_PUBLISH_ERROR");
        row.setLastErrorMessage("bad token");
        when(pubRepo.findById(row.getId())).thenReturn(Optional.of(row));

        service.retry(row.getId(), null);

        assertEquals(PublicationStatus.RETRY_SCHEDULED, row.getStatus());
        assertEquals(0, row.getRetryCount());
        assertNull(row.getLastErrorCode());
        assertNotNull(row.getNextAttemptAt());
        verify(dispatcher, never()).dispatch(any(), any(), any());
    }
}
