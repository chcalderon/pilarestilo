package com.pilarestilo.product.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductDto(
        UUID id,
        String name,
        String description,
        BigDecimal priceAmount,
        String priceCurrency,
        BigDecimal listPriceAmount,
        String listPriceCurrency,
        String imageUrl,
        String condition,
        String brand,
        int stock,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        BigDecimal avgRating,
        int reviewCount,
        String shippingOriginZone,
        /** Null when the admin has not stated one; the storefront then derives it from the categories. */
        String variantType,
        List<SizeStockDto> sizeStocks,
        List<String> categorySlugs,
        List<String> categoryTypes,
        List<VariantDto> variants
) {
    public record SizeStockDto(String size, int stock) {}
    public record VariantDto(String color, String size, int stock, int stockOnHand, int stockReserved, int stockAvailable) {}
}
