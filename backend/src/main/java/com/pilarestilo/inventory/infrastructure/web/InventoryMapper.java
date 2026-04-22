package com.pilarestilo.inventory.infrastructure.web;

import com.pilarestilo.inventory.application.dto.InventoryProductDto;
import com.pilarestilo.product.application.dto.ProductDto;

import java.util.Collections;
import java.util.List;

public final class InventoryMapper {

    private InventoryMapper() {
    }

    public static InventoryProductDto toDto(ProductDto product, int lowStockThreshold) {
        int normalizedThreshold = Math.max(lowStockThreshold, 0);
        boolean lowStock = product.stock() <= normalizedThreshold;
        List<ProductDto.SizeStockDto> sizeStocks = product.sizeStocks() == null ? Collections.emptyList() : product.sizeStocks();
        List<ProductDto.VariantDto> variants = product.variants() == null ? Collections.emptyList() : product.variants();
        List<String> categories = product.categorySlugs() == null ? Collections.emptyList() : product.categorySlugs();
        return new InventoryProductDto(
                product.id(),
                product.name(),
                product.brand(),
                product.condition(),
                product.stock(),
                lowStock,
                product.active(),
                product.updatedAt(),
                sizeStocks.stream()
                        .map(s -> new InventoryProductDto.SizeStockDto(s.size(), s.stock()))
                        .toList(),
                categories,
                variants.stream()
                        .map(v -> new InventoryProductDto.VariantDto(v.color(), v.size(), v.stock()))
                        .toList()
        );
    }
}
