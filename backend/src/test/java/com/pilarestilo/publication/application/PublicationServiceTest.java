package com.pilarestilo.publication.application;

import tools.jackson.databind.ObjectMapper;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.commands.CreatePublicationCommand;
import com.pilarestilo.publication.application.dto.CreatePublicationResult;
import com.pilarestilo.publication.application.dto.PublicationBatchDetailDto;
import com.pilarestilo.publication.application.dto.PublicationBatchSummaryDto;
import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.dto.PublicationDto;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationMediaBundleType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationSourceType;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.domain.events.PublicationApproved;
import com.pilarestilo.publication.domain.events.PublicationDispatchCompleted;
import com.pilarestilo.publication.domain.events.PublicationDispatchRequested;
import com.pilarestilo.publication.domain.events.PublicationDraftCreated;
import com.pilarestilo.publication.domain.events.PublicationSubmittedForReview;
import com.pilarestilo.publication.domain.enums.PublicationMediaRenderStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMediaBundleEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationBatchJpaRepository;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicationServiceTest {

    @Mock
    PublicationBatchJpaRepository publicationBatchRepository;

    @Mock
    PublicationJpaRepository publicationRepository;

    @Mock
    ProductRepository productRepository;

    @Mock
    PublicationDispatcher publicationDispatcher;

    @Mock
    DomainEventPublisher eventPublisher;

    PublicationService service;

    @BeforeEach
    void setUp() {
        service = new PublicationService(
                publicationBatchRepository,
                publicationRepository,
                productRepository,
                publicationDispatcher,
                eventPublisher,
                new ObjectMapper(),
                new DispatchBackoffPolicy(java.util.List.of(2, 10, 30, 120, 360))
        );
        lenient().when(publicationRepository.save(any(PublicationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void create_builds_draft_with_snapshot_and_bundle() {
        when(publicationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        CreatePublicationResult result = service.create(new CreatePublicationCommand(
                null,
                PublicationSourceType.PRODUCT,
                null,
                PublicationPlatform.INSTAGRAM,
                PublicationChannelType.FEED_POST,
                "es-CL",
                "Invierno Boutique",
                "Copy aprobado",
                List.of("#pilarestilo", "#invierno"),
                true,
                null,
                "pub-123",
                List.of(new CreatePublicationCommand.MediaBundleCommand(
                        PublicationMediaBundleType.SOCIAL_FEED,
                        "https://cdn.example.com/social-feed.jpg",
                        Map.of("targetAspectRatio", "4:5")
                )),
                null
        ), UUID.randomUUID());

        assertTrue(result.created());
        PublicationDto dto = result.publication();
        assertEquals(PublicationStatus.DRAFT, dto.status());
        assertEquals(1, dto.mediaBundles().size());
        assertEquals(1, dto.snapshots().size());
        assertEquals("CONTENT_INPUT", dto.snapshots().get(0).snapshotType().name());
        verify(eventPublisher).publish(any(PublicationDraftCreated.class));
    }

    @Test
    void create_with_a_scheduled_at_puts_the_row_in_scheduled() {
        when(publicationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        CreatePublicationResult result = service.create(new CreatePublicationCommand(
                null, PublicationSourceType.PRODUCT, null,
                PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
                "es-CL", null, "Copy", List.of(), false,
                java.time.Instant.now().plusSeconds(3600), "pub-sched-1", List.of(), null
        ), UUID.randomUUID());

        assertEquals(PublicationStatus.SCHEDULED, result.publication().status());
        assertEquals(com.pilarestilo.publication.domain.enums.PublicationApprovalStatus.NOT_REQUIRED,
                result.publication().approvalStatus());
    }

    @Test
    void create_returns_existing_publication_when_idempotency_key_is_reused() {
        PublicationEntity existing = new PublicationEntity();
        existing.setId(UUID.randomUUID());
        existing.setSourceType(PublicationSourceType.MANUAL);
        existing.setPlatform(PublicationPlatform.FACEBOOK);
        existing.setChannelType(PublicationChannelType.FEED_POST);
        existing.setStatus(PublicationStatus.DRAFT);
        existing.setApprovalStatus(com.pilarestilo.publication.domain.enums.PublicationApprovalStatus.PENDING_REVIEW);
        existing.setLocale("es-CL");
        existing.setIdempotencyKey("pub-123");
        existing.setContentVersion(1);
        existing.setSnapshotVersion(0);
        existing.setRetryCount(0);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(existing.getCreatedAt());
        existing.setMediaBundles(List.of());
        existing.setAttempts(List.of());
        existing.setReviews(List.of());
        existing.setSnapshots(List.of());
        when(publicationRepository.findByIdempotencyKey("pub-123")).thenReturn(Optional.of(existing));

        CreatePublicationResult result = service.create(new CreatePublicationCommand(
                null,
                PublicationSourceType.MANUAL,
                null,
                PublicationPlatform.FACEBOOK,
                PublicationChannelType.FEED_POST,
                "es-CL",
                null,
                "Copy",
                List.of(),
                true,
                null,
                "pub-123",
                List.of(),
                null
        ), UUID.randomUUID());

        assertFalse(result.created());
        assertEquals(existing.getId(), result.publication().id());
        verify(publicationRepository, never()).save(any(PublicationEntity.class));
    }

    @Test
    void dispatch_from_approved_publication_creates_attempt_and_publishes_event() {
        UUID publicationId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PublicationEntity entity = approvedPublication(publicationId, productId);
        when(publicationRepository.findById(publicationId)).thenReturn(Optional.of(entity));
        when(productRepository.findById(productId)).thenReturn(Optional.of(
                Product.create("Chaqueta", "desc", new Money(BigDecimal.valueOf(49990), "CLP"),
                        "https://img", ProductCondition.NEW, "Pilar", 2)
        ));
        when(publicationDispatcher.dispatch(any(), anyString(), any()))
                .thenReturn(new PublicationDispatcher.DispatchResult(
                        "req-1", "hash-1", PublicationAttemptStatus.SUCCEEDED, "remote-1", null, null,
                        "https://www.instagram.com/p/x/", true));

        PublicationDto dto = service.dispatch(publicationId, UUID.randomUUID());

        assertEquals(PublicationStatus.PUBLISHED, dto.status());
        assertEquals("remote-1", dto.externalPostId());
        assertEquals(1, dto.attempts().size());
        assertEquals("req-1", dto.attempts().get(0).requestId());
        assertEquals(2, dto.snapshots().size());
        verify(eventPublisher).publish(any(PublicationDispatchCompleted.class));
    }

    @Test
    void dispatch_rejects_non_approved_publication() {
        UUID publicationId = UUID.randomUUID();
        PublicationEntity entity = approvedPublication(publicationId, null);
        entity.setStatus(PublicationStatus.DRAFT);
        when(publicationRepository.findById(publicationId)).thenReturn(Optional.of(entity));
        UUID actorId = UUID.randomUUID();

        assertThrows(DomainException.class, () -> service.dispatch(publicationId, actorId));
    }

    @Test
    void dispatch_persists_failure_without_losing_the_record_when_dispatcher_reports_failure() {
        UUID publicationId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PublicationEntity entity = approvedPublication(publicationId, productId);
        when(publicationRepository.findById(publicationId)).thenReturn(Optional.of(entity));
        when(productRepository.findById(productId)).thenReturn(Optional.of(
                Product.create("Chaqueta", "desc", new Money(BigDecimal.valueOf(49990), "CLP"),
                        "https://img", ProductCondition.NEW, "Pilar", 2)
        ));
        when(publicationDispatcher.dispatch(any(), anyString(), any()))
                .thenReturn(new PublicationDispatcher.DispatchResult(
                        "req-1", "hash-1", PublicationAttemptStatus.FAILED, null,
                        "INSTAGRAM_PUBLISH_ERROR", "Rate limited", null, false));

        PublicationDto dto = service.dispatch(publicationId, UUID.randomUUID());

        // Regression test for the rollback bug: before the fix, dispatchInternal rethrew after
        // saving, which rolled back that same save — the FAILED status and error never actually
        // reached the database. Now it must.
        assertEquals(PublicationStatus.FAILED, dto.status());
        assertEquals("INSTAGRAM_PUBLISH_ERROR", dto.lastErrorCode());
        assertEquals("Rate limited", dto.lastErrorMessage());
        verify(publicationRepository, atLeastOnce()).save(any(PublicationEntity.class));
    }

    @Test
    void dispatch_that_throws_unexpectedly_is_persisted_as_a_scheduled_retry() {
        UUID publicationId = UUID.randomUUID();
        PublicationEntity entity = approvedPublication(publicationId, null);
        when(publicationRepository.findById(publicationId)).thenReturn(Optional.of(entity));
        when(publicationDispatcher.dispatch(any(), anyString(), any()))
                .thenThrow(new RuntimeException("connection reset"));

        PublicationDto dto = service.dispatch(publicationId, UUID.randomUUID());

        // An unexpected throw is treated as transient — the row is scheduled for another attempt,
        // not lost (regression test for the old rollback bug: the FAILED save was rethrown away).
        assertEquals(PublicationStatus.RETRY_SCHEDULED, dto.status());
        assertEquals("connection reset", dto.lastErrorMessage());
    }

    @Test
    void retry_of_a_failed_publication_reschedules_it_for_the_worker() {
        UUID publicationId = UUID.randomUUID();
        PublicationEntity entity = approvedPublication(publicationId, null);
        entity.setStatus(PublicationStatus.FAILED);
        entity.setRetryCount(5);
        entity.setLastErrorCode("INSTAGRAM_PUBLISH_ERROR");
        entity.setLastErrorMessage("Rate limited");
        when(publicationRepository.findById(publicationId)).thenReturn(Optional.of(entity));

        PublicationDto dto = service.retry(publicationId, UUID.randomUUID());

        assertEquals(PublicationStatus.RETRY_SCHEDULED, dto.status());
        assertEquals(0, dto.retryCount());
        assertNull(dto.lastErrorCode());
        verify(publicationDispatcher, never()).dispatch(any(), anyString(), any());
    }

    @Test
    void retry_of_a_non_failed_publication_is_rejected() {
        UUID publicationId = UUID.randomUUID();
        PublicationEntity entity = approvedPublication(publicationId, null);
        when(publicationRepository.findById(publicationId)).thenReturn(Optional.of(entity));
        UUID actorId = UUID.randomUUID();
        assertThrows(DomainException.class, () -> service.retry(publicationId, actorId));
    }

    @Test
    void submit_and_approve_transition_publication() {
        UUID publicationId = UUID.randomUUID();
        PublicationEntity entity = approvedPublication(publicationId, null);
        entity.setStatus(PublicationStatus.DRAFT);
        entity.setApprovalStatus(com.pilarestilo.publication.domain.enums.PublicationApprovalStatus.PENDING_REVIEW);
        when(publicationRepository.findById(publicationId)).thenReturn(Optional.of(entity));

        PublicationDto inReview = service.submitForReview(publicationId, UUID.randomUUID());
        assertEquals(PublicationStatus.IN_REVIEW, inReview.status());
        verify(eventPublisher).publish(any(PublicationSubmittedForReview.class));

        PublicationDto approved = service.approve(publicationId, UUID.randomUUID(), "ok");
        assertEquals(PublicationStatus.APPROVED, approved.status());
        verify(eventPublisher).publish(any(PublicationApproved.class));
    }

    @Test
    void list_batches_summarizes_each_batch_by_status() {
        UUID batchId = UUID.randomUUID();
        PublicationBatchEntity batch = new PublicationBatchEntity();
        batch.setId(batchId);
        batch.setCaptionTemplate("{producto}");
        batch.setHashtagsJson("[\"#pilarestilo\"]");
        batch.setCampaignLabel("Liquidacion");
        batch.setCreatedAt(Instant.now());
        when(publicationBatchRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(batch));

        PublicationEntity ok = batchRow(batchId, PublicationStatus.PUBLISHED, PublicationPlatform.INSTAGRAM);
        PublicationEntity bad = batchRow(batchId, PublicationStatus.FAILED, PublicationPlatform.FACEBOOK);
        when(publicationRepository.findByBatchIdInOrderByCreatedAtAsc(List.of(batchId)))
                .thenReturn(List.of(ok, bad));

        List<PublicationBatchSummaryDto> result = service.listBatches();

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).total());
        assertEquals(1, result.get(0).published());
        assertEquals(1, result.get(0).failed());
        assertTrue(result.get(0).platforms().contains(PublicationPlatform.INSTAGRAM));
        assertTrue(result.get(0).platforms().contains(PublicationPlatform.FACEBOOK));
    }

    @Test
    void get_batch_resolves_product_names_and_rows() {
        UUID batchId = UUID.randomUUID();
        PublicationBatchEntity batch = new PublicationBatchEntity();
        batch.setId(batchId);
        batch.setCaptionTemplate("{producto}");
        batch.setHashtagsJson("[\"#pilarestilo\"]");
        batch.setCreatedAt(Instant.now());
        when(publicationBatchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        Product product = Product.create("Chaqueta", "desc", new Money(BigDecimal.valueOf(1000), "CLP"),
                "https://img/x.jpg", ProductCondition.NEW, "Pilar", 1);
        UUID productId = product.getId();

        PublicationEntity row = batchRow(batchId, PublicationStatus.FAILED, PublicationPlatform.INSTAGRAM);
        row.setProductId(productId);
        row.setLastErrorCode("INSTAGRAM_PUBLISH_ERROR");
        row.setLastErrorMessage("Rate limited");
        when(publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(List.of(row));
        when(productRepository.findAllByIds(java.util.Set.of(productId))).thenReturn(List.of(product));

        PublicationBatchDetailDto detail = service.getBatch(batchId);

        assertEquals(1, detail.rows().size());
        assertEquals("Chaqueta", detail.rows().get(0).productName());
        assertEquals("Rate limited", detail.rows().get(0).lastErrorMessage());
        assertEquals(List.of(productId), detail.productIds());
    }

    @Test
    void get_batch_throws_for_unknown_id() {
        UUID unknown = UUID.randomUUID();
        when(publicationBatchRepository.findById(unknown)).thenReturn(Optional.empty());
        assertThrows(java.util.NoSuchElementException.class, () -> service.getBatch(unknown));
    }

    @Test
    void mark_schedule_window_missed_fails_the_row_and_emits_the_event() {
        UUID id = UUID.randomUUID();
        PublicationEntity entity = approvedPublication(id, null);
        entity.setStatus(PublicationStatus.SCHEDULED);
        when(publicationRepository.findById(id)).thenReturn(Optional.of(entity));

        PublicationDto dto = service.markScheduleWindowMissed(id);

        assertEquals(PublicationStatus.FAILED, dto.status());
        assertEquals("SCHEDULE_WINDOW_MISSED", dto.lastErrorCode());
        verify(eventPublisher).publish(any(com.pilarestilo.publication.domain.events.PublicationDispatchFailed.class));
    }

    @Test
    void mark_schedule_window_missed_rejects_a_non_scheduled_row() {
        UUID id = UUID.randomUUID();
        PublicationEntity entity = approvedPublication(id, null);
        when(publicationRepository.findById(id)).thenReturn(Optional.of(entity));
        assertThrows(DomainException.class, () -> service.markScheduleWindowMissed(id));
    }

    @Test
    void cancel_scheduled_batch_flips_only_scheduled_rows_to_cancelled() {
        UUID batchId = UUID.randomUUID();
        PublicationBatchEntity batch = scheduledBatch(batchId);
        when(publicationBatchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        PublicationEntity sched = batchRow(batchId, PublicationStatus.SCHEDULED, PublicationPlatform.INSTAGRAM);
        PublicationEntity done = batchRow(batchId, PublicationStatus.PUBLISHED, PublicationPlatform.FACEBOOK);
        when(publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(List.of(sched, done));

        service.cancelScheduledBatch(batchId);

        assertEquals(PublicationStatus.CANCELLED, sched.getStatus());
        assertEquals(PublicationStatus.PUBLISHED, done.getStatus());
    }

    @Test
    void cancel_scheduled_batch_rejects_a_batch_with_nothing_scheduled() {
        UUID batchId = UUID.randomUUID();
        when(publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(
                List.of(batchRow(batchId, PublicationStatus.PUBLISHED, PublicationPlatform.INSTAGRAM)));
        assertThrows(DomainException.class, () -> service.cancelScheduledBatch(batchId));
    }

    @Test
    void reschedule_batch_updates_the_batch_and_the_scheduled_rows() {
        UUID batchId = UUID.randomUUID();
        PublicationBatchEntity batch = scheduledBatch(batchId);
        when(publicationBatchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        PublicationEntity sched = batchRow(batchId, PublicationStatus.SCHEDULED, PublicationPlatform.INSTAGRAM);
        when(publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(List.of(sched));

        Instant newTime = Instant.now().plusSeconds(7200);
        service.rescheduleBatch(batchId, newTime);

        assertEquals(newTime, batch.getScheduledAt());
        assertEquals(newTime, sched.getScheduledAt());
    }

    private PublicationBatchEntity scheduledBatch(UUID batchId) {
        PublicationBatchEntity batch = new PublicationBatchEntity();
        batch.setId(batchId);
        batch.setCaptionTemplate("{producto}");
        batch.setHashtagsJson("[]");
        batch.setCreatedAt(Instant.now());
        batch.setScheduledAt(Instant.now().plusSeconds(3600));
        return batch;
    }

    private PublicationEntity batchRow(UUID batchId, PublicationStatus status, PublicationPlatform platform) {
        PublicationEntity e = approvedPublication(UUID.randomUUID(), UUID.randomUUID());
        e.setBatchId(batchId);
        e.setStatus(status);
        e.setPlatform(platform);
        return e;
    }

    private static PublicationMediaBundleEntity bundleWithManifest(String primary, List<String> imageUrls) {
        PublicationMediaBundleEntity b = new PublicationMediaBundleEntity();
        b.setId(UUID.randomUUID());
        b.setBundleType(PublicationMediaBundleType.SOCIAL_FEED);
        b.setPrimaryAssetUrl(primary);
        b.setAssetManifest(imageUrls == null ? Map.of() : Map.of("imageUrls", imageUrls));
        b.setRenderStatus(PublicationMediaRenderStatus.READY);
        b.setCreatedAt(Instant.now());
        b.setUpdatedAt(Instant.now());
        return b;
    }

    @Test
    void dispatch_passes_the_full_image_list_from_the_manifest_to_the_dispatcher() {
        UUID publicationId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PublicationEntity entity = approvedPublication(publicationId, productId);
        entity.setMediaBundles(new java.util.ArrayList<>(List.of(
                bundleWithManifest("https://img/a.jpg", List.of("https://img/a.jpg", "https://img/b.jpg")))));
        when(publicationRepository.findById(publicationId)).thenReturn(Optional.of(entity));
        when(productRepository.findById(productId)).thenReturn(Optional.of(
                Product.create("Chaqueta", "d", new Money(BigDecimal.valueOf(49990), "CLP"),
                        "https://img", ProductCondition.NEW, "Pilar", 2)));
        when(publicationDispatcher.dispatch(any(), anyString(), any()))
                .thenReturn(new PublicationDispatcher.DispatchResult(
                        "req-1", null, PublicationAttemptStatus.SUCCEEDED, "remote-1", null, null, null, true));

        service.dispatch(publicationId, UUID.randomUUID());

        org.mockito.ArgumentCaptor<PublicationDispatchPayload> captor =
                org.mockito.ArgumentCaptor.forClass(PublicationDispatchPayload.class);
        verify(publicationDispatcher).dispatch(any(), anyString(), captor.capture());
        assertEquals(List.of("https://img/a.jpg", "https://img/b.jpg"), captor.getValue().mediaUrls());
    }

    @Test
    void dispatch_falls_back_to_the_primary_url_when_the_manifest_has_no_image_list() {
        UUID publicationId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PublicationEntity entity = approvedPublication(publicationId, productId);
        entity.setMediaBundles(new java.util.ArrayList<>(List.of(
                bundleWithManifest("https://img/only.jpg", null))));
        when(publicationRepository.findById(publicationId)).thenReturn(Optional.of(entity));
        when(productRepository.findById(productId)).thenReturn(Optional.of(
                Product.create("Chaqueta", "d", new Money(BigDecimal.valueOf(49990), "CLP"),
                        "https://img", ProductCondition.NEW, "Pilar", 2)));
        when(publicationDispatcher.dispatch(any(), anyString(), any()))
                .thenReturn(new PublicationDispatcher.DispatchResult(
                        "req-1", null, PublicationAttemptStatus.SUCCEEDED, "remote-1", null, null, null, true));

        service.dispatch(publicationId, UUID.randomUUID());

        org.mockito.ArgumentCaptor<PublicationDispatchPayload> captor =
                org.mockito.ArgumentCaptor.forClass(PublicationDispatchPayload.class);
        verify(publicationDispatcher).dispatch(any(), anyString(), captor.capture());
        assertEquals(List.of("https://img/only.jpg"), captor.getValue().mediaUrls());
    }

    private PublicationEntity approvedPublication(UUID publicationId, UUID productId) {
        PublicationEntity entity = new PublicationEntity();
        entity.setId(publicationId);
        entity.setProductId(productId);
        entity.setSourceType(productId != null ? PublicationSourceType.PRODUCT : PublicationSourceType.MANUAL);
        entity.setSourceId(productId);
        entity.setPlatform(PublicationPlatform.INSTAGRAM);
        entity.setChannelType(PublicationChannelType.FEED_POST);
        entity.setStatus(PublicationStatus.APPROVED);
        entity.setApprovalStatus(com.pilarestilo.publication.domain.enums.PublicationApprovalStatus.APPROVED);
        entity.setCaption("Copy");
        entity.setHashtagsJson("[\"#pilarestilo\"]");
        entity.setLocale("es-CL");
        entity.setIdempotencyKey("pub-" + publicationId);
        entity.setContentVersion(1);
        entity.setSnapshotVersion(1);
        entity.setRetryCount(0);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        entity.setMediaBundles(new java.util.ArrayList<>(List.of()));
        entity.setAttempts(new java.util.ArrayList<>(List.of()));
        entity.setReviews(new java.util.ArrayList<>(List.of()));
        entity.setSnapshots(new java.util.ArrayList<>(List.of()));
        return entity;
    }
}
