package com.pilarestilo.inventoryservice.application;

import com.pilarestilo.inventoryservice.persistence.InventoryMovementEntity;
import com.pilarestilo.inventoryservice.persistence.InventoryMovementRepository;
import com.pilarestilo.inventoryservice.persistence.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryCommandServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryMovementRepository movementRepository;

    private InventoryCommandService service;

    @BeforeEach
    void setUp() {
        service = new InventoryCommandService(productRepository, movementRepository);
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
    }

    @Test
    void reserveRejectsMissingVariantSelectorWhenProductHasVariants() {
        when(productRepository.hasVariants(PRODUCT_ID)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.reserve(PRODUCT_ID, 1, null, null));

        verify(productRepository, never()).reserveStock(any(), anyInt(), any());
        verify(productRepository, never()).reserveVariantStock(any(), any(), any(), anyInt());
    }

    @Test
    void reserveWithVariantSyncsProductStockFromVariants() {
        when(productRepository.hasVariants(PRODUCT_ID)).thenReturn(true);
        when(productRepository.reserveVariantStock(PRODUCT_ID, "Base", "UNICO", 1)).thenReturn(1);

        service.reserve(PRODUCT_ID, 1, "Base", "UNICO");

        verify(productRepository).syncProductStockFromVariants(eq(PRODUCT_ID), any());
        verify(productRepository, never()).reserveStock(any(), anyInt(), any());
    }

    @Test
    void releaseWithVariantSyncsProductStockFromVariants() {
        service.release(PRODUCT_ID, 1, "Base", "UNICO");

        verify(productRepository).releaseVariantStock(PRODUCT_ID, "Base", "UNICO", 1);
        /* No size-stock call: the variant row is the only gate now. */
        verify(productRepository).syncProductStockFromVariants(eq(PRODUCT_ID), any());
        verify(productRepository, never()).releaseStock(any(), anyInt(), any());
    }

    /*
     * The ledger is the point of this service writing at all: the table existed since V57 and was
     * empty in practice, because the monolith only writes it on the local path and production sends
     * every command here.
     */
    @Test
    void recordsAReserveWithThePositiveSignTheMonolithUses() {
        when(productRepository.hasVariants(PRODUCT_ID)).thenReturn(true);
        when(productRepository.reserveVariantStock(PRODUCT_ID, "Negro", "M", 2)).thenReturn(1);

        service.reserve(PRODUCT_ID, 2, "Negro", "M");

        ArgumentCaptor<InventoryMovementEntity> line = ArgumentCaptor.forClass(InventoryMovementEntity.class);
        verify(movementRepository).save(line.capture());
        assertEquals("RESERVE", line.getValue().getType());
        assertEquals(2, line.getValue().getQuantity());
        assertEquals("Negro", line.getValue().getVariantColor());
        assertEquals("M", line.getValue().getVariantSize());
    }

    @Test
    void recordsAConfirmAsUnitsLeavingTheShelf() {
        when(productRepository.confirmVariantStock(PRODUCT_ID, "Negro", "M", 1)).thenReturn(1);

        service.confirm(PRODUCT_ID, 1, "Negro", "M");

        ArgumentCaptor<InventoryMovementEntity> line = ArgumentCaptor.forClass(InventoryMovementEntity.class);
        verify(movementRepository).save(line.capture());
        assertEquals("CONFIRM", line.getValue().getType());
        assertEquals(-1, line.getValue().getQuantity());
    }

    @Test
    void recordsAReleaseAsUnitsComingBack() {
        service.release(PRODUCT_ID, 3, "Negro", "M");

        ArgumentCaptor<InventoryMovementEntity> line = ArgumentCaptor.forClass(InventoryMovementEntity.class);
        verify(movementRepository).save(line.capture());
        assertEquals("RELEASE", line.getValue().getType());
        assertEquals(-3, line.getValue().getQuantity());
    }

    /** A refused command changed nothing, so it must leave no line claiming it did. */
    @Test
    void writesNoLineWhenTheStockWasNotThere() {
        when(productRepository.hasVariants(PRODUCT_ID)).thenReturn(true);
        when(productRepository.reserveVariantStock(PRODUCT_ID, "Negro", "M", 99)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.reserve(PRODUCT_ID, 99, "Negro", "M"));

        verifyNoInteractions(movementRepository);
    }
}