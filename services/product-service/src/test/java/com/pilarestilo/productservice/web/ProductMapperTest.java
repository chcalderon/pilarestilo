package com.pilarestilo.productservice.web;

import com.pilarestilo.productservice.persistence.CategoryEntity;
import com.pilarestilo.productservice.persistence.ProductEntity;
import com.pilarestilo.productservice.persistence.ProductSizeStockEmbeddable;
import com.pilarestilo.productservice.persistence.ProductVariantEmbeddable;
import com.pilarestilo.productservice.web.dto.ProductDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductMapperTest {

    @Test
    void maps_entity_to_dto_with_sorted_categories_and_variants() {
        ProductEntity entity = new ProductEntity();
        UUID productId = UUID.randomUUID();
        ReflectionTestUtils.setField(entity, "id", productId);
        ReflectionTestUtils.setField(entity, "name", "Cartera");
        ReflectionTestUtils.setField(entity, "description", "Cartera cuero");
        ReflectionTestUtils.setField(entity, "priceAmount", new BigDecimal("79000.00"));
        ReflectionTestUtils.setField(entity, "priceCurrency", "CLP");
        ReflectionTestUtils.setField(entity, "listPriceAmount", new BigDecimal("99000.00"));
        ReflectionTestUtils.setField(entity, "listPriceCurrency", "CLP");
        ReflectionTestUtils.setField(entity, "imageUrl", "https://img");
        ReflectionTestUtils.setField(entity, "condition", "NUEVO");
        ReflectionTestUtils.setField(entity, "brand", "Prada");
        ReflectionTestUtils.setField(entity, "stock", 3);
        ReflectionTestUtils.setField(entity, "active", true);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-01-02T00:00:00Z"));
        ReflectionTestUtils.setField(entity, "avgRating", new BigDecimal("4.50"));
        ReflectionTestUtils.setField(entity, "reviewCount", 10);
        ReflectionTestUtils.setField(entity, "shippingOriginZone", "LOCAL");

        ProductSizeStockEmbeddable size = new ProductSizeStockEmbeddable();
        ReflectionTestUtils.setField(size, "size", "M");
        ReflectionTestUtils.setField(size, "stock", 2);
        ProductVariantEmbeddable variant = new ProductVariantEmbeddable();
        ReflectionTestUtils.setField(variant, "color", "Negro");
        ReflectionTestUtils.setField(variant, "size", "M");
        ReflectionTestUtils.setField(variant, "stockOnHand", 5);
        ReflectionTestUtils.setField(variant, "stockReserved", 2);
        ReflectionTestUtils.setField(entity, "sizeStocks", List.of(size));
        ReflectionTestUtils.setField(entity, "variants", List.of(variant));

        CategoryEntity catB = new CategoryEntity();
        ReflectionTestUtils.setField(catB, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(catB, "slug", "zapatos");
        ReflectionTestUtils.setField(catB, "categoryType", "SHOES");
        CategoryEntity catA = new CategoryEntity();
        ReflectionTestUtils.setField(catA, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(catA, "slug", "accesorios");
        ReflectionTestUtils.setField(catA, "categoryType", "ACCESSORY");
        ReflectionTestUtils.setField(entity, "categories", Set.of(catB, catA));

        ProductDto dto = ProductMapper.toDto(entity);

        assertEquals(productId, dto.id());
        assertEquals("Cartera", dto.name());
        assertEquals(1, dto.sizeStocks().size());
        assertEquals("M", dto.sizeStocks().get(0).size());
        assertEquals(1, dto.variants().size());
        assertEquals("Negro", dto.variants().get(0).color());
        assertEquals(5, dto.variants().get(0).stock());
        assertEquals(5, dto.variants().get(0).stockOnHand());
        assertEquals(2, dto.variants().get(0).stockReserved());
        assertEquals(3, dto.variants().get(0).stockAvailable());
        assertEquals(List.of("accesorios", "zapatos"), dto.categorySlugs());
        assertEquals(List.of("ACCESSORY", "SHOES"), dto.categoryTypes());
    }
}
