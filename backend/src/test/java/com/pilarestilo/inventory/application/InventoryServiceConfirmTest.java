package com.pilarestilo.inventory.application;

import com.pilarestilo.inventory.domain.model.StockMovementOrigin;
import com.pilarestilo.inventory.domain.ports.InventoryMovementRepository;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceConfirmTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private InventoryMovementRepository inventoryMovementRepository;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private Product product;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
        lenient().when(inventoryMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        inventoryService = new InventoryService(
                productRepository,
                eventPublisher,
                inventoryMovementRepository,
                restClientBuilder,
                false,
                "http://localhost:8082"
        );
    }

    // ── variant path ──────────────────────────────────────────────────────────

    @Test
    void confirm_withVariant_callsAtomicConfirmVariantStock() {
        UUID productId = UUID.randomUUID();
        when(productRepository.atomicConfirmVariantStock(any(UUID.class), anyString(), anyString(), anyInt()))
                .thenReturn(1);

        assertDoesNotThrow(() -> inventoryService.confirm(productId, 3, "Rojo", "M", StockMovementOrigin.unknown()));

        verify(productRepository).atomicConfirmVariantStock(productId, "Rojo", "M", 3);
        verify(productRepository, never()).findById(any());
    }

    @Test
    void confirm_withVariant_throwsDomainException_whenNoRowsUpdated() {
        UUID productId = UUID.randomUUID();
        when(productRepository.atomicConfirmVariantStock(any(UUID.class), anyString(), anyString(), anyInt()))
                .thenReturn(0);
        StockMovementOrigin origin = StockMovementOrigin.unknown();

        assertThrows(DomainException.class, () ->
                inventoryService.confirm(productId, 3, "Rojo", "M", origin)
        );
    }

    @Test
    void confirm_withVariant_doesNotCallRelease_orReserve() {
        UUID productId = UUID.randomUUID();
        when(productRepository.atomicConfirmVariantStock(any(UUID.class), anyString(), anyString(), anyInt()))
                .thenReturn(1);

        inventoryService.confirm(productId, 2, "Azul", "L", StockMovementOrigin.unknown());

        verify(productRepository, never()).atomicReserveVariantStock(any(), any(), any(), anyInt());
        verify(productRepository, never()).atomicReleaseVariantStock(any(), any(), any(), anyInt());
    }

    // ── legacy (non-variant) path ─────────────────────────────────────────────

    @Test
    void confirm_withNullVariant_isNoOpForLegacyAggregateProducts() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertDoesNotThrow(() -> inventoryService.confirm(productId, 1, StockMovementOrigin.unknown()));

        // Existence guard runs but no stock mutation occurs — confirm is a no-op for legacy products.
        verify(productRepository).findById(productId);
        verify(productRepository, never()).atomicConfirmVariantStock(any(), any(), any(), anyInt());
    }

    // ── posSale path ──────────────────────────────────────────────────────────

    @Test
    void posSale_withVariant_callsAtomicConfirmVariantStock() {
        UUID productId = UUID.randomUUID();
        when(productRepository.atomicConfirmVariantStock(any(UUID.class), anyString(), anyString(), anyInt()))
                .thenReturn(1);

        assertDoesNotThrow(() -> inventoryService.posSale(productId, 2, "Rojo", "M", StockMovementOrigin.unknown()));

        verify(productRepository).atomicConfirmVariantStock(productId, "Rojo", "M", 2);
    }

    @Test
    void posSale_withVariant_throwsDomainException_whenNoRowsUpdated() {
        UUID productId = UUID.randomUUID();
        when(productRepository.atomicConfirmVariantStock(any(UUID.class), anyString(), anyString(), anyInt()))
                .thenReturn(0);
        StockMovementOrigin origin = StockMovementOrigin.unknown();

        assertThrows(DomainException.class, () ->
                inventoryService.posSale(productId, 1, "Rojo", "M", origin)
        );
    }
}
