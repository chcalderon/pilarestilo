package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.application.commands.PublishProductsBatchCommand;
import com.pilarestilo.publication.application.dto.PublicationBatchDetailDto;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationBatchJpaRepository;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Replaces the SCHEDULED rows of a not-yet-published batch with a fresh set built from a new
 * command. Not @Transactional over the loop: create() is its own transaction on PublicationService
 * (same reasoning as PublishProductsBatchUseCase). The guard + delete + batch-field update run as
 * individual repository writes first; if a crash lands between the delete and the regeneration the
 * batch is left empty, which the owner sees in Historial and can re-edit.
 */
@Component
public class UpdateScheduledBatchUseCase {

    private final PublicationService publicationService;
    private final PublicationJpaRepository publicationRepository;
    private final PublicationBatchJpaRepository publicationBatchRepository;
    private final ProductRepository productRepository;
    private final BatchPublicationFactory factory;

    public UpdateScheduledBatchUseCase(PublicationService publicationService,
                                       PublicationJpaRepository publicationRepository,
                                       PublicationBatchJpaRepository publicationBatchRepository,
                                       ProductRepository productRepository,
                                       BatchPublicationFactory factory) {
        this.publicationService = publicationService;
        this.publicationRepository = publicationRepository;
        this.publicationBatchRepository = publicationBatchRepository;
        this.productRepository = productRepository;
        this.factory = factory;
    }

    public PublicationBatchDetailDto execute(UUID batchId, PublishProductsBatchCommand command, UUID actorUserId) {
        PublicationBatchEntity batch = publicationBatchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Publication batch not found: " + batchId));
        List<PublicationEntity> rows = publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        boolean allScheduled = !rows.isEmpty()
                && rows.stream().allMatch(r -> r.getStatus() == PublicationStatus.SCHEDULED);
        if (!allScheduled) {
            throw new DomainException("Esta tanda ya empezó a publicarse, no se puede editar");
        }
        publicationRepository.deleteAll(rows);
        batch.setCaptionTemplate(command.captionTemplate());
        batch.setHashtagsJson(factory.serializeHashtags(command.hashtags()));
        batch.setCampaignLabel(factory.trimToNull(command.campaignLabel()));
        batch.setScheduledAt(command.scheduledAt());
        publicationBatchRepository.save(batch);

        for (UUID productId : command.productIds()) {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) {
                continue;
            }
            String caption = factory.interpolate(command.captionTemplate(), productId, product,
                    command.variantSelections().get(productId));
            for (PublicationPlatform platform : command.platforms()) {
                publicationService.create(
                        factory.buildCreateCommand(command, productId, product, platform, caption, batchId), actorUserId);
            }
        }
        return publicationService.getBatch(batchId);
    }
}
