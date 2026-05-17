package com.pilarestilo.inventoryservice.web;

import com.pilarestilo.inventoryservice.persistence.CategoryEntity;
import com.pilarestilo.inventoryservice.persistence.ProductEntity;
import com.pilarestilo.inventoryservice.persistence.ProductSizeStockEmbeddable;
import com.pilarestilo.inventoryservice.persistence.ProductVariantEmbeddable;
import com.pilarestilo.inventoryservice.web.dto.InventoryProductDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryMapperTest {

    @Test
    void maps_entity_and_applies_low_stock_threshold() {
        ProductEntity entity = new ProductEntity();
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "name", "Vestido");
        ReflectionTestUtils.setField(entity, "brand", "Prada");
        ReflectionTestUtils.setField(entity, "condition", "NUEVO");
        ReflectionTestUtils.setField(entity, "stock", 2);
        ReflectionTestUtils.setField(entity, "active", true);
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-01-01T00:00:00Z"));

        ProductSizeStockEmbeddable size = new ProductSizeStockEmbeddable();
        ReflectionTestUtils.setField(size, "size", "M");
        ReflectionTestUtils.setField(size, "stock", 1);
        ReflectionTestUtils.setField(entity, "sizeStocks", List.of(size));

        ProductVariantEmbeddable variant = new ProductVariantEmbeddable();
        ReflectionTestUtils.setField(variant, "color", "Negro");
        ReflectionTestUtils.setField(variant, "size", "M");
        ReflectionTestUtils.setField(variant, "stockOnHand", 4);
        ReflectionTestUtils.setField(variant, "stockReserved", 1);
        ReflectionTestUtils.setField(entity, "variants", List.of(variant));

        CategoryEntity catB = new CategoryEntity();
        ReflectionTestUtils.setField(catB, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(catB, "slug", "zapatos");
        CategoryEntity catA = new CategoryEntity();
        ReflectionTestUtils.setField(catA, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(catA, "slug", "accesorios");
        ReflectionTestUtils.setField(entity, "categories", Set.of(catB, catA));

        InventoryProductDto dto = InventoryMapper.toDto(entity, 2);

        assertEquals(id, dto.id());
        assertTrue(dto.lowStock());
        assertEquals(List.of("accesorios", "zapatos"), dto.categorySlugs());
        assertEquals("M", dto.sizeStocks().get(0).size());
        assertEquals("Negro", dto.variants().get(0).color());
        assertEquals(4, dto.variants().get(0).stockOnHand());
        assertEquals(1, dto.variants().get(0).stockReserved());
        assertEquals(3, dto.variants().get(0).stockAvailable());
        assertEquals(3, dto.variants().get(0).stock());
    }
}
