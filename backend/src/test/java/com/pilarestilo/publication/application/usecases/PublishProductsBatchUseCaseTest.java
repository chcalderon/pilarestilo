package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
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
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishProductsBatchUseCaseTest {

    @Mock PublicationService publicationService;
    @Mock ProductRepository productRepository;

    PublishProductsBatchUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PublishProductsBatchUseCase(publicationService, productRepository);
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
                "Liquidacion"
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
                "{producto}", List.of(), null
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
                List.of(productId), Set.of(PublicationPlatform.FACEBOOK), "{producto}", List.of(), null
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
                List.of(productId), Set.of(PublicationPlatform.INSTAGRAM), "{producto}", List.of(), null
        ), UUID.randomUUID());

        assertFalse(result.items().get(0).success());
        assertEquals("Instagram credentials are not configured", result.items().get(0).errorMessage());
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
                List.of(), List.of(), List.of(), List.of()
        );
    }
}
