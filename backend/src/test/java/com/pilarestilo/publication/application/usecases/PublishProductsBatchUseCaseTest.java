package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.model.ProductVariant;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.application.commands.CreatePublicationCommand;
import com.pilarestilo.publication.application.commands.PublishProductsBatchCommand;
import com.pilarestilo.publication.application.dto.CreatePublicationResult;
import com.pilarestilo.publication.application.dto.PublicationDto;
import com.pilarestilo.publication.application.dto.PublishProductsBatchResult;
import com.pilarestilo.publication.domain.enums.PublicationApprovalStatus;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationSourceType;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationBatchJpaRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishProductsBatchUseCaseTest {

    @Mock PublicationService publicationService;
    @Mock ProductRepository productRepository;
    @Mock PublicationBatchJpaRepository publicationBatchRepository;

    PublishProductsBatchUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PublishProductsBatchUseCase(
                publicationService, productRepository, publicationBatchRepository, new ObjectMapper());
        lenient().when(publicationBatchRepository.save(any(PublicationBatchEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void interpolates_caption_template_per_product_and_dispatches_each_selected_platform() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create("Chaqueta", "desc", new Money(BigDecimal.valueOf(49990), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 2);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        UUID publicationId = UUID.randomUUID();
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenReturn(new CreatePublicationResult(publishedDto(publicationId), true));
        when(publicationService.dispatch(eq(publicationId), any()))
                .thenReturn(publishedDto(publicationId));

        PublishProductsBatchResult result = useCase.execute(new PublishProductsBatchCommand(
                List.of(productId),
                Set.of(PublicationPlatform.INSTAGRAM, PublicationPlatform.FACEBOOK),
                "{producto} a solo {precio}!",
                List.of("#pilarestilo"),
                "Liquidacion",
                Map.of(),
                Map.of()
        ), UUID.randomUUID());

        assertEquals(2, result.items().size());
        assertTrue(result.items().stream().allMatch(PublishProductsBatchResult.PublicationItemResult::success));

        ArgumentCaptor<CreatePublicationCommand> captor = ArgumentCaptor.forClass(CreatePublicationCommand.class);
        verify(publicationService, times(2)).create(captor.capture(), any());
        assertTrue(captor.getAllValues().stream()
                .allMatch(cmd -> cmd.caption().equals("Chaqueta a solo $49.990!")));
        assertTrue(captor.getAllValues().stream()
                .allMatch(cmd -> cmd.campaignLabel().equals("Liquidacion")));
        assertTrue(captor.getAllValues().stream().noneMatch(CreatePublicationCommand::approvalRequired));
    }

    @Test
    void one_missing_product_does_not_stop_the_rest_of_the_batch() {
        UUID okProductId = UUID.randomUUID();
        UUID missingProductId = UUID.randomUUID();
        Product okProduct = Product.create("Chaqueta", "desc", new Money(BigDecimal.valueOf(10000), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 2);
        when(productRepository.findById(okProductId)).thenReturn(Optional.of(okProduct));
        when(productRepository.findById(missingProductId)).thenReturn(Optional.empty());

        UUID publicationId = UUID.randomUUID();
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenReturn(new CreatePublicationResult(publishedDto(publicationId), true));
        when(publicationService.dispatch(eq(publicationId), any()))
                .thenReturn(publishedDto(publicationId));

        PublishProductsBatchResult result = useCase.execute(new PublishProductsBatchCommand(
                List.of(missingProductId, okProductId),
                Set.of(PublicationPlatform.INSTAGRAM),
                "{producto}", List.of(), null, Map.of(), Map.of()
        ), UUID.randomUUID());

        assertEquals(2, result.items().size());
        assertFalse(result.items().get(0).success());
        assertTrue(result.items().get(0).errorMessage().contains("no encontrado"));
        assertTrue(result.items().get(1).success());
    }

    @Test
    void a_thrown_exception_for_one_item_is_recorded_without_stopping_the_batch() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create("Bolso", "desc", new Money(BigDecimal.valueOf(5000), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 1);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenThrow(new DomainException("boom"));

        PublishProductsBatchResult result = useCase.execute(new PublishProductsBatchCommand(
                List.of(productId), Set.of(PublicationPlatform.FACEBOOK), "{producto}", List.of(), null, Map.of(), Map.of()
        ), UUID.randomUUID());

        assertEquals(1, result.items().size());
        assertFalse(result.items().get(0).success());
        assertEquals("boom", result.items().get(0).errorMessage());
    }

    @Test
    void a_dispatch_result_that_is_not_published_is_reported_as_a_failure_with_its_error() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create("Falda", "desc", new Money(BigDecimal.valueOf(8000), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 1);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        UUID publicationId = UUID.randomUUID();
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenReturn(new CreatePublicationResult(publishedDto(publicationId), true));
        when(publicationService.dispatch(eq(publicationId), any()))
                .thenReturn(failedDto(publicationId, "Instagram credentials are not configured"));

        PublishProductsBatchResult result = useCase.execute(new PublishProductsBatchCommand(
                List.of(productId), Set.of(PublicationPlatform.INSTAGRAM), "{producto}", List.of(), null, Map.of(), Map.of()
        ), UUID.randomUUID());

        assertFalse(result.items().get(0).success());
        assertEquals("Instagram credentials are not configured", result.items().get(0).errorMessage());
    }

    @Test
    void uses_the_image_override_when_provided_for_a_product() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create("Chaqueta", "desc", new Money(BigDecimal.valueOf(49990), "CLP"),
                "https://cdn.example.com/original.jpg", ProductCondition.NEW, "Pilar", 2);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        UUID publicationId = UUID.randomUUID();
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenReturn(new CreatePublicationResult(publishedDto(publicationId), true));
        when(publicationService.dispatch(eq(publicationId), any()))
                .thenReturn(publishedDto(publicationId));

        useCase.execute(new PublishProductsBatchCommand(
                List.of(productId), Set.of(PublicationPlatform.INSTAGRAM), "{producto}", List.of(), null,
                Map.of(productId, "https://cdn.example.com/edited.jpg"), Map.of()
        ), UUID.randomUUID());

        ArgumentCaptor<CreatePublicationCommand> captor = ArgumentCaptor.forClass(CreatePublicationCommand.class);
        verify(publicationService).create(captor.capture(), any());
        assertEquals("https://cdn.example.com/edited.jpg",
                captor.getValue().mediaBundles().get(0).primaryAssetUrl());
    }

    @Test
    void resolves_the_chosen_variant_into_the_color_talla_and_cantidad_tokens() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create("Chaqueta", "desc", new Money(BigDecimal.valueOf(49990), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 2);
        product.setVariants(List.of(
                new ProductVariant("Negro", "M", 5, 1),
                new ProductVariant("Rojo", "L", 3, 0)
        ));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        UUID publicationId = UUID.randomUUID();
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenReturn(new CreatePublicationResult(publishedDto(publicationId), true));
        when(publicationService.dispatch(eq(publicationId), any()))
                .thenReturn(publishedDto(publicationId));

        useCase.execute(new PublishProductsBatchCommand(
                List.of(productId), Set.of(PublicationPlatform.INSTAGRAM),
                "{producto} color {color} talla {talla}, quedan {cantidad}", List.of(), null,
                Map.of(), Map.of(productId, new PublishProductsBatchCommand.VariantSelection("Negro", "M"))
        ), UUID.randomUUID());

        ArgumentCaptor<CreatePublicationCommand> captor = ArgumentCaptor.forClass(CreatePublicationCommand.class);
        verify(publicationService).create(captor.capture(), any());
        assertEquals("Chaqueta color Negro talla M, quedan 4", captor.getValue().caption());
    }

    @Test
    void leaves_the_variant_tokens_blank_when_no_variant_is_selected() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create("Bolso", "desc", new Money(BigDecimal.valueOf(9990), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 1);
        product.setVariants(List.of(new ProductVariant("Negro", "UNICA", 5, 0)));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        UUID publicationId = UUID.randomUUID();
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenReturn(new CreatePublicationResult(publishedDto(publicationId), true));
        when(publicationService.dispatch(eq(publicationId), any()))
                .thenReturn(publishedDto(publicationId));

        useCase.execute(new PublishProductsBatchCommand(
                List.of(productId), Set.of(PublicationPlatform.INSTAGRAM),
                "{producto} color {color}", List.of(), null, Map.of(), Map.of()
        ), UUID.randomUUID());

        ArgumentCaptor<CreatePublicationCommand> captor = ArgumentCaptor.forClass(CreatePublicationCommand.class);
        verify(publicationService).create(captor.capture(), any());
        assertEquals("Bolso color ", captor.getValue().caption());
    }

    @Test
    void creates_one_batch_row_and_stamps_its_id_on_every_publication() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create("Chaqueta", "desc", new Money(BigDecimal.valueOf(49990), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 2);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        UUID publicationId = UUID.randomUUID();
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenReturn(new CreatePublicationResult(publishedDto(publicationId), true));
        when(publicationService.dispatch(eq(publicationId), any())).thenReturn(publishedDto(publicationId));

        useCase.execute(new PublishProductsBatchCommand(
                List.of(productId), Set.of(PublicationPlatform.INSTAGRAM),
                "{producto} a solo {precio}", List.of("#pilarestilo"), "Liquidacion", Map.of(), Map.of()
        ), UUID.randomUUID());

        ArgumentCaptor<PublicationBatchEntity> batchCaptor = ArgumentCaptor.forClass(PublicationBatchEntity.class);
        verify(publicationBatchRepository).save(batchCaptor.capture());
        assertEquals("{producto} a solo {precio}", batchCaptor.getValue().getCaptionTemplate());
        assertEquals("Liquidacion", batchCaptor.getValue().getCampaignLabel());

        ArgumentCaptor<CreatePublicationCommand> cmdCaptor = ArgumentCaptor.forClass(CreatePublicationCommand.class);
        verify(publicationService).create(cmdCaptor.capture(), any());
        assertEquals(batchCaptor.getValue().getId(), cmdCaptor.getValue().batchId());
    }

    private PublicationDto publishedDto(UUID id) {
        return dto(id, PublicationStatus.PUBLISHED, null);
    }

    private PublicationDto failedDto(UUID id, String errorMessage) {
        return dto(id, PublicationStatus.FAILED, errorMessage);
    }

    private PublicationDto dto(UUID id, PublicationStatus status, String lastErrorMessage) {
        return new PublicationDto(
                id, null, PublicationSourceType.PRODUCT, null,
                PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
                status, PublicationApprovalStatus.NOT_REQUIRED,
                "caption", List.of(), "es-CL", null, null, Instant.now(), "remote-1",
                "idem-1", 1, 1, null, lastErrorMessage, 0, null, null,
                Instant.now(), Instant.now(),
                List.of(), List.of(), List.of(), List.of(), null
        );
    }
}
