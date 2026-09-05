package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.application.commands.CreatePublicationCommand;
import com.pilarestilo.publication.application.commands.PublishProductsBatchCommand;
import com.pilarestilo.publication.application.dto.CreatePublicationResult;
import com.pilarestilo.publication.application.dto.PublicationDto;
import com.pilarestilo.publication.application.dto.PublishProductsBatchResult;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationBatchJpaRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates a multi-product, multi-platform publish. Deliberately not @Transactional: each
 * item's create()+dispatch() opens its own transaction on PublicationService (a different Spring
 * bean, so its @Transactional proxy applies independently). Wrapping this loop in one outer
 * transaction would mark it rollback-only the moment any single item's call threw, losing every
 * other item's already-recorded result too — the opposite of "each row is independent."
 *
 * <p>When the command carries a scheduledAt, each row is created as SCHEDULED and NOT dispatched;
 * a background job publishes it when due.
 */
@Component
public class PublishProductsBatchUseCase {

    private final PublicationService publicationService;
    private final ProductRepository productRepository;
    private final PublicationBatchJpaRepository publicationBatchRepository;
    private final BatchPublicationFactory factory;

    public PublishProductsBatchUseCase(PublicationService publicationService,
                                       ProductRepository productRepository,
                                       PublicationBatchJpaRepository publicationBatchRepository,
                                       BatchPublicationFactory factory) {
        this.publicationService = publicationService;
        this.productRepository = productRepository;
        this.publicationBatchRepository = publicationBatchRepository;
        this.factory = factory;
    }

    public PublishProductsBatchResult execute(PublishProductsBatchCommand command, UUID actorUserId) {
        List<PublishProductsBatchResult.PublicationItemResult> items = new ArrayList<>();
        boolean scheduled = command.scheduledAt() != null;

        PublicationBatchEntity batch = new PublicationBatchEntity();
        batch.setId(UUID.randomUUID());
        batch.setCaptionTemplate(command.captionTemplate());
        batch.setHashtagsJson(factory.serializeHashtags(command.hashtags()));
        batch.setCampaignLabel(factory.trimToNull(command.campaignLabel()));
        batch.setScheduledAt(command.scheduledAt());
        batch.setCreatedBy(actorUserId);
        batch.setCreatedAt(Instant.now());
        publicationBatchRepository.save(batch);

        for (UUID productId : command.productIds()) {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) {
                for (PublicationPlatform platform : command.platforms()) {
                    items.add(new PublishProductsBatchResult.PublicationItemResult(
                            productId, platform, false, null, "Producto no encontrado: " + productId, false));
                }
                continue;
            }
            String caption = factory.interpolate(command.captionTemplate(), product,
                    command.variantSelections().get(productId));
            for (PublicationPlatform platform : command.platforms()) {
                items.add(publishOne(productId, product, platform, caption, command, actorUserId, batch.getId(), scheduled));
            }
        }
        return new PublishProductsBatchResult(items);
    }

    private PublishProductsBatchResult.PublicationItemResult publishOne(UUID productId,
                                                                        Product product,
                                                                        PublicationPlatform platform,
                                                                        String caption,
                                                                        PublishProductsBatchCommand command,
                                                                        UUID actorUserId,
                                                                        UUID batchId,
                                                                        boolean scheduled) {
        try {
            CreatePublicationCommand createCommand =
                    factory.buildCreateCommand(command, productId, product, platform, caption, batchId);
            CreatePublicationResult created = publicationService.create(createCommand, actorUserId);
            if (scheduled) {
                return new PublishProductsBatchResult.PublicationItemResult(
                        productId, platform, false, created.publication().id(), null, true);
            }
            PublicationDto dispatched = publicationService.dispatch(created.publication().id(), actorUserId);
            boolean success = dispatched.status() == PublicationStatus.PUBLISHED;
            return new PublishProductsBatchResult.PublicationItemResult(
                    productId, platform, success, dispatched.id(),
                    success ? null : dispatched.lastErrorMessage(), false);
        } catch (DomainException ex) {
            return new PublishProductsBatchResult.PublicationItemResult(
                    productId, platform, false, null, ex.getMessage(), false);
        }
    }
}
