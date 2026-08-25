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
import java.util.Map;
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
        ReflectionTestUtils.setField(catB, "definesVariantFields", false);
        ReflectionTestUtils.setField(catB, "variantFieldConfig", null);
        CategoryEntity catA = new CategoryEntity();
        ReflectionTestUtils.setField(catA, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(catA, "slug", "accesorios");
        ReflectionTestUtils.setField(catA, "categoryType", "ACCESSORY");
        ReflectionTestUtils.setField(catA, "definesVariantFields", false);
        ReflectionTestUtils.setField(catA, "variantFieldConfig", null);
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
        assertEquals("Variante", dto.variantFieldConfig().primary().label());
    }

    @Test
    void maps_variantFieldConfig_fromTheOneShapeCategoryAmongAssigned() {
        ProductEntity entity = new ProductEntity();
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(entity, "name", "Zapato");
        ReflectionTestUtils.setField(entity, "description", "desc");
        ReflectionTestUtils.setField(entity, "priceAmount", new BigDecimal("50000.00"));
        ReflectionTestUtils.setField(entity, "priceCurrency", "CLP");
        ReflectionTestUtils.setField(entity, "imageUrl", "https://img");
        ReflectionTestUtils.setField(entity, "condition", "NUEVO");
        ReflectionTestUtils.setField(entity, "brand", "Marca");
        ReflectionTestUtils.setField(entity, "stock", 1);
        ReflectionTestUtils.setField(entity, "active", true);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(entity, "avgRating", BigDecimal.ZERO);
        ReflectionTestUtils.setField(entity, "reviewCount", 0);
        ReflectionTestUtils.setField(entity, "shippingOriginZone", "LOCAL");
        ReflectionTestUtils.setField(entity, "sizeStocks", List.of());
        ReflectionTestUtils.setField(entity, "variants", List.of());

        CategoryEntity mujer = new CategoryEntity();
        ReflectionTestUtils.setField(mujer, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(mujer, "slug", "mujer");
        ReflectionTestUtils.setField(mujer, "categoryType", "GENERIC");
        ReflectionTestUtils.setField(mujer, "definesVariantFields", false);
        ReflectionTestUtils.setField(mujer, "variantFieldConfig", null);

        CategoryEntity zapatos = new CategoryEntity();
        ReflectionTestUtils.setField(zapatos, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(zapatos, "slug", "zapatos");
        ReflectionTestUtils.setField(zapatos, "categoryType", "SHOES");
        ReflectionTestUtils.setField(zapatos, "definesVariantFields", true);
        ReflectionTestUtils.setField(zapatos, "variantFieldConfig", Map.of(
                "primary", Map.of("label", "Color", "inputType", "FREE_TEXT", "options", List.of(),
                        "allowMultiple", false, "allowCustom", true),
                "secondary", Map.of("label", "Numero", "inputType", "RANGE", "options", List.of(),
                        "min", 34, "max", 43, "allowMultiple", true, "allowCustom", true)
        ));
        ReflectionTestUtils.setField(entity, "categories", Set.of(mujer, zapatos));

        ProductDto dto = ProductMapper.toDto(entity);

        assertEquals("Numero", dto.variantFieldConfig().secondary().label());
        assertEquals("RANGE", dto.variantFieldConfig().secondary().inputType());
        assertEquals(34, dto.variantFieldConfig().secondary().min());
        assertEquals(43, dto.variantFieldConfig().secondary().max());
    }

    @Test
    void maps_variantFieldConfig_fallsBackToGenericWhenNoShapeCategory() {
        ProductEntity entity = new ProductEntity();
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(entity, "name", "Panuelo");
        ReflectionTestUtils.setField(entity, "description", "desc");
        ReflectionTestUtils.setField(entity, "priceAmount", new BigDecimal("10000.00"));
        ReflectionTestUtils.setField(entity, "priceCurrency", "CLP");
        ReflectionTestUtils.setField(entity, "imageUrl", "https://img");
        ReflectionTestUtils.setField(entity, "condition", "NUEVO");
        ReflectionTestUtils.setField(entity, "brand", "Marca");
        ReflectionTestUtils.setField(entity, "stock", 1);
        ReflectionTestUtils.setField(entity, "active", true);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(entity, "avgRating", BigDecimal.ZERO);
        ReflectionTestUtils.setField(entity, "reviewCount", 0);
        ReflectionTestUtils.setField(entity, "shippingOriginZone", "LOCAL");
        ReflectionTestUtils.setField(entity, "sizeStocks", List.of());
        ReflectionTestUtils.setField(entity, "variants", List.of());

        CategoryEntity mujer = new CategoryEntity();
        ReflectionTestUtils.setField(mujer, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(mujer, "slug", "mujer");
        ReflectionTestUtils.setField(mujer, "categoryType", "GENERIC");
        ReflectionTestUtils.setField(mujer, "definesVariantFields", false);
        ReflectionTestUtils.setField(mujer, "variantFieldConfig", null);
        ReflectionTestUtils.setField(entity, "categories", Set.of(mujer));

        ProductDto dto = ProductMapper.toDto(entity);

        assertEquals("Variante", dto.variantFieldConfig().primary().label());
        assertEquals("Detalle", dto.variantFieldConfig().secondary().label());
        assertEquals("FREE_TEXT", dto.variantFieldConfig().primary().inputType());
    }
}
