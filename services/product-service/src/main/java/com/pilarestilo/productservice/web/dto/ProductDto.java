package com.pilarestilo.productservice.web.dto;

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
        List<SizeStockDto> sizeStocks,
        List<String> categorySlugs,
        List<String> categoryTypes,
        /** Null when unstated; the storefront then derives it from categoryTypes. */
        String variantType,
        List<VariantDto> variants
) {
    public record SizeStockDto(String size, int stock) {}
    public record VariantDto(String color, String size, int stock, int stockOnHand, int stockReserved, int stockAvailable) {}
}
