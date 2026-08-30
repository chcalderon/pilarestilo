package com.pilarestilo.inventory.application;

import com.pilarestilo.inventory.domain.model.StockMovementOrigin;
import com.pilarestilo.inventory.domain.enums.InventoryMovementType;
import com.pilarestilo.inventory.domain.model.InventoryMovement;
import com.pilarestilo.inventory.domain.ports.InventoryMovementRepository;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryMovementsRecordedOnReserveTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private InventoryMovementRepository inventoryMovementRepository;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        when(inventoryMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        inventoryService = new InventoryService(
                productRepository,
                eventPublisher,
                inventoryMovementRepository
        );
    }

    @Test
    void reserve_withVariant_recordsMovementWithTypeReserveAndPositiveQty() {
        UUID productId = UUID.randomUUID();
        when(productRepository.atomicReserveVariantStock(any(UUID.class), anyString(), anyString(), anyInt()))
                .thenReturn(1);

        inventoryService.reserve(productId, 3, "Rojo", "M", StockMovementOrigin.unknown());

        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryMovementRepository).save(captor.capture());

        InventoryMovement recorded = captor.getValue();
        assertEquals(InventoryMovementType.RESERVE, recorded.getType());
        assertEquals(3, recorded.getQuantity());
        assertEquals(productId, recorded.getProductId());
        assertEquals("Rojo", recorded.getVariantColor());
        assertEquals("M", recorded.getVariantSize());
    }

    @Test
    void reserve_withoutVariant_recordsMovementWithTypeReserveAndPositiveQty() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create(
                "Test Product",
                null,
                Money.of(BigDecimal.valueOf(10000)),
                null,
                ProductCondition.NEW,
                "Brand",
                5
        );
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);

        inventoryService.reserve(productId, 2, StockMovementOrigin.unknown());

        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryMovementRepository).save(captor.capture());

        InventoryMovement recorded = captor.getValue();
        assertEquals(InventoryMovementType.RESERVE, recorded.getType());
        assertEquals(2, recorded.getQuantity());
        assertEquals(productId, recorded.getProductId());
        assertNull(recorded.getVariantColor());
        assertNull(recorded.getVariantSize());
    }
}
