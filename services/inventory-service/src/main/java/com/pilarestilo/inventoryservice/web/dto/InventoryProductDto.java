package com.pilarestilo.inventoryservice.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryProductDto(
        UUID id,
        String name,
        String brand,
        String condition,
        int stock,
        boolean lowStock,
        boolean active,
        Instant updatedAt,
        List<SizeStockDto> sizeStocks,
        List<String> categorySlugs,
        List<VariantDto> variants
) {
    public record SizeStockDto(String size, int stock) {}
    public record VariantDto(String color, String size, int stock) {}
}
