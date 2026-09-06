package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.application.dto.PublicationBatchDetailDto;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Re-dispatches the FAILED rows of a batch. Deliberately not @Transactional: each retry is its
 * own @Transactional call on PublicationService (a different bean), so a failure in one does not
 * roll back the others — same reasoning as PublishProductsBatchUseCase.
 */
@Component
public class RetryFailedBatchUseCase {

    private final PublicationService publicationService;
    private final PublicationJpaRepository publicationRepository;

    public RetryFailedBatchUseCase(PublicationService publicationService,
                                   PublicationJpaRepository publicationRepository) {
        this.publicationService = publicationService;
        this.publicationRepository = publicationRepository;
    }

    public PublicationBatchDetailDto execute(UUID batchId, UUID actorUserId) {
        publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId).stream()
                .filter(p -> p.getStatus() == PublicationStatus.FAILED)
                .forEach(p -> {
                    try {
                        publicationService.retry(p.getId(), actorUserId);
                    } catch (RuntimeException _) {
                        // Row is no longer FAILED (retried elsewhere, or state changed) — skip it.
                    }
                });
        return publicationService.getBatch(batchId);
    }
}
