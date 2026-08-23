package com.pilarestilo.inventory.application;

import com.pilarestilo.inventory.domain.model.StockMovementOrigin;
import com.pilarestilo.inventory.domain.ports.InventoryMovementRepository;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
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
import com.pilarestilo.shared.domain.DomainException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceReleaseVariantTest {

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

    @Test
    void release_withVariant_callsAtomicRelease() {
        UUID productId = UUID.randomUUID();
        when(productRepository.atomicReleaseVariantStock(any(UUID.class), anyString(), anyString(), anyInt()))
                .thenReturn(1);

        assertDoesNotThrow(() -> inventoryService.release(productId, 2, "Rojo", "M", StockMovementOrigin.unknown()));

        verify(productRepository).atomicReleaseVariantStock(productId, "Rojo", "M", 2);
        verify(productRepository, never()).findById(any());
    }

    @Test
    void release_withVariant_throwsDomainException_whenVariantNotFound() {
        UUID productId = UUID.randomUUID();
        when(productRepository.atomicReleaseVariantStock(any(UUID.class), anyString(), anyString(), anyInt()))
                .thenReturn(0);
        StockMovementOrigin origin = StockMovementOrigin.unknown();

        assertThrows(DomainException.class, () -> inventoryService.release(productId, 2, "Verde", "XL", origin));
    }

    @Test
    void release_withNullVariant_usesLegacyPath() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);

        assertDoesNotThrow(() -> inventoryService.release(productId, 1, StockMovementOrigin.unknown()));

        verify(productRepository).findById(productId);
        verify(productRepository, never()).atomicReleaseVariantStock(any(), any(), any(), anyInt());
    }
}
