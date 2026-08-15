package com.pilarestilo.inventoryservice.web;

import com.pilarestilo.inventoryservice.persistence.ProductEntity;
import com.pilarestilo.inventoryservice.persistence.ProductVariantEmbeddable;
import com.pilarestilo.inventoryservice.web.dto.InventoryProductDto;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

final class InventoryMapper {

    private InventoryMapper() {
    }

    static InventoryProductDto toDto(ProductEntity entity, int lowStockThreshold) {
        int normalizedThreshold = Math.max(lowStockThreshold, 0);

        /*
         * Derived from the variants rather than read from product_size_stocks.
         *
         * <p>That table held the same fact twice: available units per size, which the variant
         * rows already give by summing. Two writers kept it — this service decremented it on
         * every reservation while the monolith overwrote it from the variants on every product
         * save — so it drifted, and a drifted row refused a sale the variants would have allowed.
         * Summing here cannot disagree with itself.
         */
        Map<String, Integer> availableBySize = new LinkedHashMap<>();
        for (ProductVariantEmbeddable variant : entity.getVariants()) {
            availableBySize.merge(variant.getSize(), variant.available(), Integer::sum);
        }
        List<InventoryProductDto.SizeStockDto> sizeStocks = availableBySize.entrySet().stream()
                .map(e -> new InventoryProductDto.SizeStockDto(e.getKey(), e.getValue()))
                .toList();

        List<InventoryProductDto.VariantDto> variants = entity.getVariants().stream()
                .map(v -> new InventoryProductDto.VariantDto(
                        v.getColor(),
                        v.getSize(),
                        v.getStock(),
                        v.getStockOnHand(),
                        v.getStockReserved(),
                        v.available()
                ))
                .toList();

        List<String> categorySlugs = entity.getCategories().stream()
                .map(c -> c.getSlug())
                .sorted(Comparator.naturalOrder())
                .toList();

        return new InventoryProductDto(
                entity.getId(),
                entity.getName(),
                entity.getBrand(),
                entity.getCondition(),
                entity.getStock(),
                entity.getStock() <= normalizedThreshold,
                entity.isActive(),
                entity.getUpdatedAt(),
                sizeStocks,
                categorySlugs,
                variants
        );
    }
}
