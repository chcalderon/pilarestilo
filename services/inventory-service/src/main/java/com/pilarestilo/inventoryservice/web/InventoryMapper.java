package com.pilarestilo.inventoryservice.web;

import com.pilarestilo.inventoryservice.persistence.ProductEntity;
import com.pilarestilo.inventoryservice.web.dto.InventoryProductDto;

import java.util.Comparator;
import java.util.List;

final class InventoryMapper {

    private InventoryMapper() {
    }

    static InventoryProductDto toDto(ProductEntity entity, int lowStockThreshold) {
        int normalizedThreshold = Math.max(lowStockThreshold, 0);

        List<InventoryProductDto.SizeStockDto> sizeStocks = entity.getSizeStocks().stream()
                .map(s -> new InventoryProductDto.SizeStockDto(s.getSize(), s.getStock()))
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
