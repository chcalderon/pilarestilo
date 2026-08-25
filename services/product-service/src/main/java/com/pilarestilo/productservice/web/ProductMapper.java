package com.pilarestilo.productservice.web;

import com.pilarestilo.productservice.persistence.CategoryEntity;
import com.pilarestilo.productservice.persistence.ProductEntity;
import com.pilarestilo.productservice.web.dto.ProductDto;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ProductMapper {

    private ProductMapper() {}

    static ProductDto toDto(ProductEntity entity) {
        List<ProductDto.SizeStockDto> sizeStocks = entity.getSizeStocks().stream()
                .map(s -> new ProductDto.SizeStockDto(s.getSize(), s.getStock()))
                .toList();

        List<ProductDto.VariantDto> variants = entity.getVariants().stream()
                .map(v -> new ProductDto.VariantDto(
                        v.getColor(),
                        v.getSize(),
                        v.getStockOnHand(),
                        v.getStockOnHand(),
                        v.getStockReserved(),
                        v.available()
                ))
                .toList();

        List<String> categorySlugs = entity.getCategories().stream()
                .map(CategoryEntity::getSlug)
                .sorted(Comparator.naturalOrder())
                .toList();

        return new ProductDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPriceAmount(),
                entity.getPriceCurrency(),
                entity.getListPriceAmount(),
                entity.getListPriceCurrency(),
                entity.getImageUrl(),
                entity.getCondition(),
                entity.getBrand(),
                entity.getStock(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getAvgRating(),
                entity.getReviewCount(),
                entity.getShippingOriginZone(),
                sizeStocks,
                categorySlugs,
                resolveVariantFieldConfig(entity.getCategories()),
                variants
        );
    }

    /**
     * The same "at most one shape category" rule as the monolith's
     * ShapeCategoryResolver, reimplemented here because this is a separate
     * deployable sharing no code with it -- see product/domain/model/ShapeCategoryResolver.java
     * in the monolith for the canonical version this must stay behaviorally
     * identical to.
     */
    private static ProductDto.ProductVariantFieldConfigDto resolveVariantFieldConfig(java.util.Set<CategoryEntity> categories) {
        List<CategoryEntity> shapeCategories = categories.stream().filter(CategoryEntity::isDefinesVariantFields).toList();
        if (shapeCategories.isEmpty()) {
            return genericFallback();
        }
        // product-service only reads; it never creates/updates products, so an
        // already-invalid 2+-shape-category product (which the monolith's write
        // path now rejects going forward) is read here defensively rather than
        // thrown on -- picking the first is a display-only tie-break for data
        // that predates this feature, not a new rule.
        Optional<CategoryEntity> resolved = shapeCategories.stream().findFirst();
        return toConfigDto(resolved.map(CategoryEntity::getVariantFieldConfig).orElse(null));
    }

    private static ProductDto.ProductVariantFieldConfigDto genericFallback() {
        var field = new ProductDto.ProductVariantFieldConfigDto.FieldDto(
                "Variante", "FREE_TEXT", List.of(), null, null, true, true);
        var detail = new ProductDto.ProductVariantFieldConfigDto.FieldDto(
                "Detalle", "FREE_TEXT", List.of(), null, null, true, true);
        return new ProductDto.ProductVariantFieldConfigDto(field, detail);
    }

    @SuppressWarnings("unchecked")
    private static ProductDto.ProductVariantFieldConfigDto toConfigDto(Map<String, Object> raw) {
        if (raw == null) return genericFallback();
        return new ProductDto.ProductVariantFieldConfigDto(
                toFieldDto((Map<String, Object>) raw.get("primary")),
                toFieldDto((Map<String, Object>) raw.get("secondary")));
    }

    @SuppressWarnings("unchecked")
    private static ProductDto.ProductVariantFieldConfigDto.FieldDto toFieldDto(Map<String, Object> raw) {
        List<String> options = raw.get("options") == null
                ? List.of()
                : ((List<Object>) raw.get("options")).stream().map(String::valueOf).toList();
        return new ProductDto.ProductVariantFieldConfigDto.FieldDto(
                (String) raw.get("label"),
                (String) raw.get("inputType"),
                options,
                raw.get("min") == null ? null : ((Number) raw.get("min")).intValue(),
                raw.get("max") == null ? null : ((Number) raw.get("max")).intValue(),
                Boolean.TRUE.equals(raw.get("allowMultiple")),
                Boolean.TRUE.equals(raw.get("allowCustom")));
    }
}
