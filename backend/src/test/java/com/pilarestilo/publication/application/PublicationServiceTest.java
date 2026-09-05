package com.pilarestilo.publication.application;

import tools.jackson.databind.ObjectMapper;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.commands.CreatePublicationCommand;
import com.pilarestilo.publication.application.dto.CreatePublicationResult;
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
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
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
                publicationRepository,
                productRepository,
                publicationDispatcher,
                eventPublisher,
                new ObjectMapper()
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
                ))
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
                List.of()
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
                        "req-1", "hash-1", PublicationAttemptStatus.SUCCEEDED, "remote-1", null, null));

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
                        "INSTAGRAM_PUBLISH_ERROR", "Rate limited"));

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
    void dispatch_persists_failure_even_when_the_dispatcher_throws_unexpectedly() {
        UUID publicationId = UUID.randomUUID();
        PublicationEntity entity = approvedPublication(publicationId, null);
        when(publicationRepository.findById(publicationId)).thenReturn(Optional.of(entity));
        when(publicationDispatcher.dispatch(any(), anyString(), any()))
                .thenThrow(new RuntimeException("connection reset"));

        PublicationDto dto = service.dispatch(publicationId, UUID.randomUUID());

        assertEquals(PublicationStatus.FAILED, dto.status());
        assertEquals("connection reset", dto.lastErrorMessage());
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
