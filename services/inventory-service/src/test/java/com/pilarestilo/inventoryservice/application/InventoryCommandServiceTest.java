package com.pilarestilo.inventoryservice.application;

import com.pilarestilo.inventoryservice.persistence.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

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

    private InventoryCommandService service;

    @BeforeEach
    void setUp() {
        service = new InventoryCommandService(productRepository);
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
}
