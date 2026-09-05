package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.application.commands.CreatePublicationCommand;
import com.pilarestilo.publication.application.commands.PublishProductsBatchCommand;
import com.pilarestilo.publication.application.dto.CreatePublicationResult;
import com.pilarestilo.publication.application.dto.PublicationBatchDetailDto;
import com.pilarestilo.publication.application.dto.PublicationDto;
import com.pilarestilo.publication.domain.enums.PublicationApprovalStatus;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationSourceType;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationBatchJpaRepository;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateScheduledBatchUseCaseTest {

    @Mock PublicationService publicationService;
    @Mock PublicationJpaRepository publicationRepository;
    @Mock PublicationBatchJpaRepository publicationBatchRepository;
    @Mock ProductRepository productRepository;

    UpdateScheduledBatchUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateScheduledBatchUseCase(publicationService, publicationRepository,
                publicationBatchRepository, productRepository, new BatchPublicationFactory(new ObjectMapper()));
    }

    private PublicationEntity row(UUID batchId, PublicationStatus status) {
        PublicationEntity e = new PublicationEntity();
        e.setId(UUID.randomUUID());
        e.setBatchId(batchId);
        e.setStatus(status);
        e.setPlatform(PublicationPlatform.INSTAGRAM);
        return e;
    }

    private PublishProductsBatchCommand cmd(UUID productId, Instant when) {
        return new PublishProductsBatchCommand(List.of(productId), Set.of(PublicationPlatform.INSTAGRAM),
                "{producto}", List.of("#x"), "Camp", Map.of(), Map.of(), when);
    }

    private PublicationBatchEntity batch(UUID batchId) {
        PublicationBatchEntity b = new PublicationBatchEntity();
        b.setId(batchId);
        b.setCaptionTemplate("old");
        b.setHashtagsJson("[]");
        b.setCreatedAt(Instant.now());
        return b;
    }

    @Test
    void replaces_scheduled_rows_and_updates_the_batch() {
        UUID batchId = UUID.randomUUID();
        Product product = Product.create("Zapatos", "d", new Money(BigDecimal.valueOf(1000), "CLP"),
                "https://img/z.jpg", ProductCondition.NEW, "Pilar", 5);
        when(publicationBatchRepository.findById(batchId)).thenReturn(Optional.of(batch(batchId)));
        PublicationEntity old = row(batchId, PublicationStatus.SCHEDULED);
        when(publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(List.of(old));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenReturn(new CreatePublicationResult(scheduledDto(UUID.randomUUID()), true));
        when(publicationService.getBatch(batchId)).thenReturn(detailStub(batchId));

        useCase.execute(batchId, cmd(product.getId(), Instant.now().plusSeconds(3600)), UUID.randomUUID());

        verify(publicationRepository).deleteAll(List.of(old));
        verify(publicationService).create(any(CreatePublicationCommand.class), any());
        verify(publicationService, never()).dispatch(any(), any());
    }

    @Test
    void refuses_when_a_row_already_left_scheduled() {
        UUID batchId = UUID.randomUUID();
        when(publicationBatchRepository.findById(batchId)).thenReturn(Optional.of(batch(batchId)));
        when(publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(List.of(
                row(batchId, PublicationStatus.SCHEDULED), row(batchId, PublicationStatus.PUBLISHING)));

        assertThrows(DomainException.class,
                () -> useCase.execute(batchId, cmd(UUID.randomUUID(), Instant.now().plusSeconds(60)), UUID.randomUUID()));
        verify(publicationRepository, never()).deleteAll(any());
    }

    private PublicationDto scheduledDto(UUID id) {
        return new PublicationDto(id, null, PublicationSourceType.PRODUCT, null,
                PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
                PublicationStatus.SCHEDULED, PublicationApprovalStatus.NOT_REQUIRED,
                "c", List.of(), "es-CL", null, null, null, null, "k", 1, 1, null, null, 0, null, null,
                Instant.now(), Instant.now(), List.of(), List.of(), List.of(), List.of(), null);
    }

    private PublicationBatchDetailDto detailStub(UUID batchId) {
        return new PublicationBatchDetailDto(batchId, null, "{producto}", List.of(), Instant.now(), List.of(), List.of(), null);
    }
}
