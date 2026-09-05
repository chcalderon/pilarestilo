package com.pilarestilo.publication.application;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.model.ProductVariant;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.commands.CreatePublicationCommand;
import com.pilarestilo.publication.application.dto.PublicationAttemptDto;
import com.pilarestilo.publication.application.dto.CreatePublicationResult;
import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.dto.PublicationDto;
import com.pilarestilo.publication.application.dto.PublicationMediaBundleDto;
import com.pilarestilo.publication.application.dto.PublicationReviewDto;
import com.pilarestilo.publication.application.dto.PublicationSnapshotDto;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationApprovalStatus;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import com.pilarestilo.publication.domain.enums.PublicationAttemptTriggerType;
import com.pilarestilo.publication.domain.enums.PublicationMediaRenderStatus;
import com.pilarestilo.publication.domain.enums.PublicationReviewAction;
import com.pilarestilo.publication.domain.enums.PublicationSnapshotType;
import com.pilarestilo.publication.domain.enums.PublicationSourceType;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.domain.events.PublicationApproved;
import com.pilarestilo.publication.domain.events.PublicationDispatchCompleted;
import com.pilarestilo.publication.domain.events.PublicationDispatchFailed;
import com.pilarestilo.publication.domain.events.PublicationDraftCreated;
import com.pilarestilo.publication.domain.events.PublicationRejected;
import com.pilarestilo.publication.domain.events.PublicationSubmittedForReview;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationAttemptEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMediaBundleEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationReviewEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationSnapshotEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationBatchJpaRepository;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import com.pilarestilo.publication.application.dto.PublicationBatchDetailDto;
import com.pilarestilo.publication.application.dto.PublicationBatchSummaryDto;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PublicationService {

    private static final String DISPATCH_ERROR_CODE = "DISPATCH_ERROR";

    private final PublicationBatchJpaRepository publicationBatchRepository;
    private final PublicationJpaRepository publicationRepository;
    private final ProductRepository productRepository;
    private final PublicationDispatcher publicationDispatcher;
    private final DomainEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public PublicationService(PublicationBatchJpaRepository publicationBatchRepository,
                              PublicationJpaRepository publicationRepository,
                              ProductRepository productRepository,
                              PublicationDispatcher publicationDispatcher,
                              DomainEventPublisher eventPublisher,
                              ObjectMapper objectMapper) {
        this.publicationBatchRepository = publicationBatchRepository;
        this.publicationRepository = publicationRepository;
        this.productRepository = productRepository;
        this.publicationDispatcher = publicationDispatcher;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CreatePublicationResult create(CreatePublicationCommand command, UUID actorUserId) {
        String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
        PublicationEntity existing = publicationRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return new CreatePublicationResult(toDto(existing), false);
        }

        Instant now = Instant.now();
        PublicationEntity entity = new PublicationEntity();
        entity.setId(UUID.randomUUID());
        entity.setProductId(command.productId());
        entity.setSourceType(command.sourceType());
        entity.setSourceId(command.sourceId());
        entity.setPlatform(command.platform());
        entity.setChannelType(command.channelType());
        PublicationStatus initialStatus;
        if (command.approvalRequired()) {
            initialStatus = PublicationStatus.DRAFT;
        } else if (command.scheduledAt() != null) {
            initialStatus = PublicationStatus.SCHEDULED;
        } else {
            initialStatus = PublicationStatus.APPROVED;
        }
        entity.setStatus(initialStatus);
        entity.setApprovalStatus(command.approvalRequired() ? PublicationApprovalStatus.PENDING_REVIEW : PublicationApprovalStatus.NOT_REQUIRED);
        entity.setCaption(trimToNull(command.caption()));
        entity.setHashtagsJson(writeHashtags(command.hashtags()));
        entity.setLocale(normalizeLocale(command.locale()));
        entity.setCampaignLabel(trimToNull(command.campaignLabel()));
        entity.setBatchId(command.batchId());
        entity.setScheduledAt(command.scheduledAt());
        entity.setIdempotencyKey(idempotencyKey);
        entity.setContentVersion(1);
        entity.setSnapshotVersion(0);
        entity.setRetryCount(0);
        entity.setCreatedBy(actorUserId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        if (!command.approvalRequired()) {
            entity.setApprovedBy(actorUserId);
        }

        entity.setMediaBundles(new ArrayList<>());
        entity.setAttempts(new ArrayList<>());
        entity.setReviews(new ArrayList<>());
        entity.setSnapshots(new ArrayList<>());

        for (CreatePublicationCommand.MediaBundleCommand bundle : command.mediaBundles()) {
            entity.getMediaBundles().add(toBundleEntity(entity, bundle, now));
        }
        addSnapshot(entity, PublicationSnapshotType.CONTENT_INPUT, buildContentSnapshot(entity), now);

        PublicationEntity saved = publicationRepository.save(entity);
        eventPublisher.publish(new PublicationDraftCreated(saved.getId(), saved.getPlatform(), saved.getChannelType(), saved.getSourceType()));
        return new CreatePublicationResult(toDto(saved), true);
    }

    @Transactional(readOnly = true)
    public List<PublicationDto> list() {
        return publicationRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PublicationDto get(UUID id) {
        return toDto(findById(id));
    }

    @Transactional(readOnly = true)
    public List<PublicationBatchSummaryDto> listBatches() {
        List<PublicationBatchEntity> batches = publicationBatchRepository.findAllByOrderByCreatedAtDesc();
        List<UUID> ids = batches.stream().map(PublicationBatchEntity::getId).toList();
        Map<UUID, List<PublicationEntity>> byBatch = ids.isEmpty() ? Map.of()
                : publicationRepository.findByBatchIdInOrderByCreatedAtAsc(ids).stream()
                        .collect(Collectors.groupingBy(PublicationEntity::getBatchId));

        List<PublicationBatchSummaryDto> out = new ArrayList<>();
        for (PublicationBatchEntity b : batches) {
            out.add(summarize(b.getId(), b.getCampaignLabel(), b.getCreatedAt(), b.getScheduledAt(),
                    byBatch.getOrDefault(b.getId(), List.of())));
        }
        List<PublicationEntity> orphans = publicationRepository.findByBatchIdIsNullOrderByCreatedAtAsc();
        if (!orphans.isEmpty()) {
            out.add(summarize(null, null, orphans.get(orphans.size() - 1).getCreatedAt(), null, orphans));
        }
        return out;
    }

    private PublicationBatchSummaryDto summarize(UUID batchId, String label, Instant createdAt,
                                                 Instant scheduledAt, List<PublicationEntity> rows) {
        EnumSet<PublicationPlatform> platforms = EnumSet.noneOf(PublicationPlatform.class);
        int published = 0;
        int failed = 0;
        int scheduled = 0;
        int pending = 0;
        for (PublicationEntity r : rows) {
            platforms.add(r.getPlatform());
            switch (r.getStatus()) {
                case PUBLISHED -> published++;
                case FAILED -> failed++;
                case SCHEDULED -> scheduled++;
                default -> pending++;
            }
        }
        return new PublicationBatchSummaryDto(batchId, label, createdAt, platforms,
                rows.size(), published, failed, scheduled, pending, scheduledAt);
    }

    @Transactional(readOnly = true)
    public PublicationBatchDetailDto getBatch(UUID batchId) {
        PublicationBatchEntity batch = publicationBatchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Publication batch not found: " + batchId));
        List<PublicationEntity> rows = publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        Set<UUID> productIds = rows.stream()
                .map(PublicationEntity::getProductId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, Product> products = productIds.isEmpty() ? Map.of()
                : productRepository.findAllByIds(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));

        List<PublicationBatchDetailDto.Row> dtoRows = rows.stream().map(r -> {
            Product p = r.getProductId() == null ? null : products.get(r.getProductId());
            return new PublicationBatchDetailDto.Row(
                    r.getId(), r.getProductId(),
                    p != null ? p.getName() : "(producto eliminado)",
                    p != null ? p.getImageUrl() : null,
                    r.getPlatform(), r.getStatus(), r.getExternalPermalink(),
                    r.getLastErrorCode(), r.getLastErrorMessage(),
                    bundleImageUrls(r));
        }).toList();

        return new PublicationBatchDetailDto(
                batch.getId(), batch.getCampaignLabel(), batch.getCaptionTemplate(),
                readHashtags(batch.getHashtagsJson()), batch.getCreatedAt(),
                new ArrayList<>(productIds), dtoRows, batch.getScheduledAt());
    }

    @Transactional
    public PublicationDto submitForReview(UUID id, UUID actorUserId) {
        PublicationEntity entity = findById(id);
        if (!(entity.getStatus() == PublicationStatus.DRAFT || entity.getStatus() == PublicationStatus.AI_READY || entity.getStatus() == PublicationStatus.REJECTED)) {
            throw new DomainException("Publication cannot be submitted for review from status " + entity.getStatus());
        }
        entity.setStatus(PublicationStatus.IN_REVIEW);
        entity.setApprovalStatus(PublicationApprovalStatus.PENDING_REVIEW);
        entity.setUpdatedAt(Instant.now());
        addReview(entity, PublicationReviewAction.SUBMIT_FOR_REVIEW, actorUserId, null, entity.getUpdatedAt());
        PublicationEntity saved = publicationRepository.save(entity);
        eventPublisher.publish(new PublicationSubmittedForReview(saved.getId()));
        return toDto(saved);
    }

    @Transactional
    public PublicationDto approve(UUID id, UUID actorUserId, String comment) {
        PublicationEntity entity = findById(id);
        if (!(entity.getStatus() == PublicationStatus.IN_REVIEW || entity.getApprovalStatus() == PublicationApprovalStatus.NOT_REQUIRED)) {
            throw new DomainException("Publication cannot be approved from status " + entity.getStatus());
        }
        entity.setStatus(PublicationStatus.APPROVED);
        entity.setApprovalStatus(PublicationApprovalStatus.APPROVED);
        entity.setApprovedBy(actorUserId);
        entity.setUpdatedAt(Instant.now());
        addReview(entity, PublicationReviewAction.APPROVE, actorUserId, comment, entity.getUpdatedAt());
        PublicationEntity saved = publicationRepository.save(entity);
        eventPublisher.publish(new PublicationApproved(saved.getId()));
        return toDto(saved);
    }

    @Transactional
    public PublicationDto reject(UUID id, UUID actorUserId, String comment) {
        PublicationEntity entity = findById(id);
        if (!(entity.getStatus() == PublicationStatus.IN_REVIEW || entity.getStatus() == PublicationStatus.APPROVED)) {
            throw new DomainException("Publication cannot be rejected from status " + entity.getStatus());
        }
        entity.setStatus(PublicationStatus.REJECTED);
        entity.setApprovalStatus(PublicationApprovalStatus.REJECTED);
        entity.setUpdatedAt(Instant.now());
        addReview(entity, PublicationReviewAction.REJECT, actorUserId, comment, entity.getUpdatedAt());
        PublicationEntity saved = publicationRepository.save(entity);
        eventPublisher.publish(new PublicationRejected(saved.getId()));
        return toDto(saved);
    }

    @Transactional
    public PublicationDto dispatch(UUID id, UUID actorUserId) {
        return dispatchInternal(id, PublicationAttemptTriggerType.MANUAL);
    }

    @Transactional
    public PublicationDto retry(UUID id, UUID actorUserId) {
        PublicationEntity entity = findById(id);
        if (entity.getStatus() != PublicationStatus.FAILED) {
            throw new DomainException("Only FAILED publications can be retried");
        }
        entity.setRetryCount(entity.getRetryCount() + 1);
        // dispatchInternal only accepts APPROVED/SCHEDULED — a retry has to move the row back out
        // of FAILED before re-dispatching, or the guard throws and rolls the retry count back.
        entity.setStatus(PublicationStatus.APPROVED);
        entity.setLastErrorCode(null);
        entity.setLastErrorMessage(null);
        publicationRepository.save(entity);
        return dispatchInternal(id, PublicationAttemptTriggerType.RETRY);
    }

    @Transactional
    public PublicationDto markScheduleWindowMissed(UUID id) {
        PublicationEntity entity = findById(id);
        if (entity.getStatus() != PublicationStatus.SCHEDULED) {
            throw new DomainException("Publication is not scheduled: " + entity.getStatus());
        }
        entity.setStatus(PublicationStatus.FAILED);
        entity.setLastErrorCode("SCHEDULE_WINDOW_MISSED");
        entity.setLastErrorMessage(
                "La hora programada ya pasó; no se publicó automáticamente. Publícala o reprográmala a mano.");
        entity.setUpdatedAt(Instant.now());
        PublicationEntity saved = publicationRepository.save(entity);
        eventPublisher.publish(new PublicationDispatchFailed(saved.getId(), 0, "SCHEDULE_WINDOW_MISSED"));
        return toDto(saved);
    }

    @Transactional
    public PublicationBatchDetailDto cancelScheduledBatch(UUID batchId) {
        List<PublicationEntity> rows = publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        List<PublicationEntity> scheduled = rows.stream()
                .filter(r -> r.getStatus() == PublicationStatus.SCHEDULED).toList();
        if (scheduled.isEmpty()) {
            throw new DomainException("Esta tanda no tiene publicaciones programadas para cancelar");
        }
        Instant now = Instant.now();
        for (PublicationEntity r : scheduled) {
            r.setStatus(PublicationStatus.CANCELLED);
            r.setUpdatedAt(now);
            publicationRepository.save(r);
        }
        return getBatch(batchId);
    }

    @Transactional
    public PublicationBatchDetailDto rescheduleBatch(UUID batchId, Instant newScheduledAt) {
        PublicationBatchEntity batch = publicationBatchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Publication batch not found: " + batchId));
        List<PublicationEntity> scheduled = publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId).stream()
                .filter(r -> r.getStatus() == PublicationStatus.SCHEDULED).toList();
        if (scheduled.isEmpty()) {
            throw new DomainException("Esta tanda ya no está programada");
        }
        Instant now = Instant.now();
        batch.setScheduledAt(newScheduledAt);
        publicationBatchRepository.save(batch);
        for (PublicationEntity r : scheduled) {
            r.setScheduledAt(newScheduledAt);
            r.setUpdatedAt(now);
            publicationRepository.save(r);
        }
        return getBatch(batchId);
    }

    private PublicationDto dispatchInternal(UUID id, PublicationAttemptTriggerType triggerType) {
        PublicationEntity entity = findById(id);
        if (!(entity.getStatus() == PublicationStatus.APPROVED || entity.getStatus() == PublicationStatus.SCHEDULED)) {
            throw new DomainException("Publication cannot be dispatched from status " + entity.getStatus());
        }

        Instant now = Instant.now();
        entity.setStatus(PublicationStatus.PUBLISHING);
        entity.setUpdatedAt(now);

        if (entity.getSourceType() == PublicationSourceType.PRODUCT) {
            UUID snapshotProductId = entity.getProductId() != null ? entity.getProductId() : entity.getSourceId();
            if (snapshotProductId != null) {
                Product product = productRepository.findById(snapshotProductId)
                        .orElseThrow(() -> new DomainException("Product snapshot source not found: " + snapshotProductId));
                addSnapshot(entity, PublicationSnapshotType.SOURCE_PRODUCT, buildProductSnapshot(product), now);
            }
        }

        PublicationDispatchPayload payload = buildDispatchPayload(entity);
        addSnapshot(entity, PublicationSnapshotType.OUTBOUND_WEBHOOK, toMap(payload), now);

        PublicationAttemptEntity attempt = new PublicationAttemptEntity();
        attempt.setId(UUID.randomUUID());
        attempt.setPublication(entity);
        attempt.setAttemptNumber(entity.getAttempts().size() + 1);
        attempt.setTriggerType(triggerType);
        attempt.setStatus(PublicationAttemptStatus.STARTED);
        attempt.setStartedAt(now);
        entity.getAttempts().add(attempt);

        PublicationDispatcher.DispatchResult result;
        try {
            result = publicationDispatcher.dispatch(entity.getId(), entity.getIdempotencyKey(), payload);
        } catch (RuntimeException ex) {
            result = new PublicationDispatcher.DispatchResult(
                    null, null, PublicationAttemptStatus.FAILED, null, DISPATCH_ERROR_CODE, ex.getMessage(), null);
        }

        Instant finishedAt = Instant.now();
        attempt.setRequestId(result.requestId());
        attempt.setPayloadHash(result.payloadHash());
        attempt.setStatus(result.status());
        attempt.setFinishedAt(finishedAt);
        attempt.setRemotePostId(result.remotePostId());
        attempt.setErrorCode(result.errorCode());
        attempt.setErrorMessage(result.errorMessage());
        entity.setUpdatedAt(finishedAt);

        if (result.status() == PublicationAttemptStatus.SUCCEEDED) {
            entity.setStatus(PublicationStatus.PUBLISHED);
            entity.setPublishedAt(finishedAt);
            entity.setExternalPostId(result.remotePostId());
            entity.setExternalPermalink(result.remotePermalink());
            entity.setLastErrorCode(null);
            entity.setLastErrorMessage(null);
            PublicationEntity saved = publicationRepository.save(entity);
            eventPublisher.publish(new PublicationDispatchCompleted(saved.getId(), attempt.getAttemptNumber(), result.remotePostId()));
            return toDto(saved);
        }

        entity.setStatus(PublicationStatus.FAILED);
        entity.setLastErrorCode(result.errorCode());
        entity.setLastErrorMessage(result.errorMessage());
        PublicationEntity saved = publicationRepository.save(entity);
        eventPublisher.publish(new PublicationDispatchFailed(saved.getId(), attempt.getAttemptNumber(), result.errorCode()));
        return toDto(saved);
    }

    private PublicationEntity findById(UUID id) {
        return publicationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Publication not found: " + id));
    }

    private PublicationMediaBundleEntity toBundleEntity(PublicationEntity publication,
                                                        CreatePublicationCommand.MediaBundleCommand bundle,
                                                        Instant now) {
        PublicationMediaBundleEntity entity = new PublicationMediaBundleEntity();
        entity.setId(UUID.randomUUID());
        entity.setPublication(publication);
        entity.setBundleType(bundle.bundleType());
        entity.setPrimaryAssetUrl(bundle.primaryAssetUrl());
        entity.setAssetManifest(bundle.assetManifest() == null ? Map.of() : bundle.assetManifest());
        entity.setRenderStatus(PublicationMediaRenderStatus.READY);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private void addSnapshot(PublicationEntity publication,
                             PublicationSnapshotType type,
                             Map<String, Object> payload,
                             Instant now) {
        PublicationSnapshotEntity entity = new PublicationSnapshotEntity();
        entity.setId(UUID.randomUUID());
        entity.setPublication(publication);
        entity.setSnapshotType(type);
        entity.setPayload(payload);
        publication.setSnapshotVersion(publication.getSnapshotVersion() + 1);
        entity.setVersion(publication.getSnapshotVersion());
        entity.setCreatedAt(now);
        publication.getSnapshots().add(entity);
    }

    private void addReview(PublicationEntity publication,
                           PublicationReviewAction action,
                           UUID actorUserId,
                           String comment,
                           Instant now) {
        PublicationReviewEntity review = new PublicationReviewEntity();
        review.setId(UUID.randomUUID());
        review.setPublication(publication);
        review.setAction(action);
        review.setActorUserId(actorUserId);
        review.setComment(trimToNull(comment));
        review.setCreatedAt(now);
        publication.getReviews().add(review);
    }

    private PublicationDispatchPayload buildDispatchPayload(PublicationEntity entity) {
        return new PublicationDispatchPayload(
                entity.getProductId(),
                entity.getPlatform(),
                entity.getChannelType(),
                entity.getCaption(),
                readHashtags(entity.getHashtagsJson()),
                bundleImageUrls(entity)
        );
    }

    /** The ordered image list for a publication: bundle[0]'s {@code assetManifest.imageUrls},
     *  falling back to its single {@code primaryAssetUrl} for pre-carousel rows. */
    private List<String> bundleImageUrls(PublicationEntity entity) {
        if (entity.getMediaBundles().isEmpty()) {
            return List.of();
        }
        PublicationMediaBundleEntity bundle = entity.getMediaBundles().get(0);
        Object raw = bundle.getAssetManifest() == null ? null : bundle.getAssetManifest().get("imageUrls");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(String::valueOf).toList();
        }
        return bundle.getPrimaryAssetUrl() == null ? List.of() : List.of(bundle.getPrimaryAssetUrl());
    }

    private Map<String, Object> buildContentSnapshot(PublicationEntity entity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", entity.getProductId() == null ? null : entity.getProductId().toString());
        payload.put("sourceType", entity.getSourceType().name());
        payload.put("sourceId", entity.getSourceId() == null ? null : entity.getSourceId().toString());
        payload.put("platform", entity.getPlatform().name());
        payload.put("channelType", entity.getChannelType().name());
        payload.put("caption", entity.getCaption());
        payload.put("hashtags", readHashtags(entity.getHashtagsJson()));
        payload.put("locale", entity.getLocale());
        payload.put("campaignLabel", entity.getCampaignLabel());
        payload.put("mediaBundleCount", entity.getMediaBundles().size());
        return payload;
    }

    private Map<String, Object> buildProductSnapshot(Product product) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", product.getId().toString());
        payload.put("name", product.getName());
        payload.put("description", product.getDescription());
        payload.put("imageUrl", product.getImageUrl());
        payload.put("condition", product.getCondition().name());
        payload.put("brand", product.getBrand().value());
        payload.put("stock", product.getStock());
        payload.put("active", product.isActive());
        payload.put("categorySlugs", product.getCategorySlugs());
        payload.put("categoryTypes", product.getCategoryTypes());
        payload.put("variants", product.getVariants().stream().map(this::toVariantMap).toList());
        return payload;
    }

    private Map<String, Object> toVariantMap(ProductVariant variant) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("color", variant.getColor());
        payload.put("size", variant.getSize());
        payload.put("stockOnHand", variant.getStockOnHand());
        payload.put("stockReserved", variant.getStockReserved());
        payload.put("stockAvailable", variant.available());
        return payload;
    }

    private Map<String, Object> toMap(PublicationDispatchPayload payload) {
        return objectMapper.convertValue(payload, new TypeReference<>() {
        });
    }

    private String normalizeIdempotencyKey(String raw) {
        String normalized = trimToNull(raw);
        return normalized != null ? normalized : "pub-" + UUID.randomUUID();
    }

    private String normalizeLocale(String raw) {
        String normalized = trimToNull(raw);
        return normalized != null ? normalized : "es-CL";
    }

    private String writeHashtags(List<String> hashtags) {
        List<String> normalized = hashtags == null ? List.of() : hashtags.stream()
                .map(this::trimToNull)
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JacksonException _) {
            throw new DomainException("Could not serialize publication hashtags");
        }
    }

    private List<String> readHashtags(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(rawJson, new TypeReference<>() {
            });
            return new ArrayList<>(new LinkedHashSet<>(parsed));
        } catch (Exception _) {
            throw new DomainException("Could not read publication hashtags");
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private PublicationDto toDto(PublicationEntity entity) {
        return new PublicationDto(
                entity.getId(),
                entity.getProductId(),
                entity.getSourceType(),
                entity.getSourceId(),
                entity.getPlatform(),
                entity.getChannelType(),
                entity.getStatus(),
                entity.getApprovalStatus(),
                entity.getCaption(),
                readHashtags(entity.getHashtagsJson()),
                entity.getLocale(),
                entity.getCampaignLabel(),
                entity.getScheduledAt(),
                entity.getPublishedAt(),
                entity.getExternalPostId(),
                entity.getIdempotencyKey(),
                entity.getContentVersion(),
                entity.getSnapshotVersion(),
                entity.getLastErrorCode(),
                entity.getLastErrorMessage(),
                entity.getRetryCount(),
                entity.getCreatedBy(),
                entity.getApprovedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getMediaBundles().stream().map(bundle -> new PublicationMediaBundleDto(
                        bundle.getId(),
                        bundle.getBundleType(),
                        bundle.getAssetManifest(),
                        bundle.getPrimaryAssetUrl(),
                        bundle.getRenderStatus(),
                        bundle.getCreatedAt(),
                        bundle.getUpdatedAt()
                )).toList(),
                entity.getAttempts().stream().map(attempt -> new PublicationAttemptDto(
                        attempt.getId(),
                        attempt.getAttemptNumber(),
                        attempt.getTriggerType(),
                        attempt.getRequestId(),
                        attempt.getWorkflowRunId(),
                        attempt.getStatus(),
                        attempt.getRemoteStatus(),
                        attempt.getRemotePostId(),
                        attempt.getErrorCode(),
                        attempt.getErrorMessage(),
                        attempt.getPayloadHash(),
                        attempt.getStartedAt(),
                        attempt.getFinishedAt()
                )).toList(),
                entity.getReviews().stream().map(review -> new PublicationReviewDto(
                        review.getId(),
                        review.getAction(),
                        review.getActorUserId(),
                        review.getComment(),
                        review.getCreatedAt()
                )).toList(),
                entity.getSnapshots().stream().map(snapshot -> new PublicationSnapshotDto(
                        snapshot.getId(),
                        snapshot.getSnapshotType(),
                        snapshot.getPayload(),
                        snapshot.getVersion(),
                        snapshot.getCreatedAt()
                )).toList(),
                entity.getExternalPermalink()
        );
    }
}
