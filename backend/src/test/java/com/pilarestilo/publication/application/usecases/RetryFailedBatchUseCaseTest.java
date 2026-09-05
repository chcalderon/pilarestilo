package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.application.dto.PublicationBatchDetailDto;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryFailedBatchUseCaseTest {

    @Mock PublicationService publicationService;
    @Mock PublicationJpaRepository publicationRepository;

    RetryFailedBatchUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RetryFailedBatchUseCase(publicationService, publicationRepository);
    }

    @Test
    void retries_only_failed_rows_and_returns_the_refreshed_detail() {
        UUID batchId = UUID.randomUUID();
        PublicationEntity failed = row(batchId, PublicationStatus.FAILED);
        PublicationEntity published = row(batchId, PublicationStatus.PUBLISHED);
        when(publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(List.of(failed, published));
        when(publicationService.getBatch(batchId)).thenReturn(
                new PublicationBatchDetailDto(batchId, null, "{producto}", List.of(), Instant.now(), List.of(), List.of(), null));

        useCase.execute(batchId, UUID.randomUUID());

        verify(publicationService).retry(eq(failed.getId()), any());
        verify(publicationService, never()).retry(eq(published.getId()), any());
    }

    @Test
    void a_row_that_raced_out_of_failed_is_skipped_without_throwing() {
        UUID batchId = UUID.randomUUID();
        PublicationEntity failed = row(batchId, PublicationStatus.FAILED);
        when(publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(List.of(failed));
        when(publicationService.retry(any(), any())).thenThrow(new DomainException("Only FAILED publications can be retried"));
        when(publicationService.getBatch(batchId)).thenReturn(
                new PublicationBatchDetailDto(batchId, null, "{producto}", List.of(), Instant.now(), List.of(), List.of(), null));

        useCase.execute(batchId, UUID.randomUUID()); // must not throw
    }

    private PublicationEntity row(UUID batchId, PublicationStatus status) {
        PublicationEntity e = new PublicationEntity();
        e.setId(UUID.randomUUID());
        e.setBatchId(batchId);
        e.setStatus(status);
        e.setPlatform(PublicationPlatform.INSTAGRAM);
        return e;
    }
}
