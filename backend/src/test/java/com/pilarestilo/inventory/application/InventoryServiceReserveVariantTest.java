package com.pilarestilo.inventory.application;

import com.pilarestilo.inventory.domain.ports.InventoryMovementRepository;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceReserveVariantTest {

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
    void reserveLocal_withVariant_throwsDomainException_whenNoRowsUpdated() {
        UUID productId = UUID.randomUUID();
        when(productRepository.atomicReserveVariantStock(any(UUID.class), anyString(), anyString(), anyInt()))
                .thenReturn(0);

        assertThrows(DomainException.class, () ->
                inventoryService.reserve(productId, 2, "Rojo", "M")
        );
    }

    @Test
    void reserveLocal_withVariant_succeeds_whenRowUpdated() {
        UUID productId = UUID.randomUUID();
        when(productRepository.atomicReserveVariantStock(any(UUID.class), anyString(), anyString(), anyInt()))
                .thenReturn(1);

        assertDoesNotThrow(() -> inventoryService.reserve(productId, 1, "Azul", "L"));
    }
}
