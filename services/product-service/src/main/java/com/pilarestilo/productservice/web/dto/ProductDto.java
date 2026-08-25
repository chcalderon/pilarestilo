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
        ProductVariantFieldConfigDto variantFieldConfig,
        List<VariantDto> variants
) {
    public record SizeStockDto(String size, int stock) {}
    public record VariantDto(String color, String size, int stock, int stockOnHand, int stockReserved, int stockAvailable) {}
    public record ProductVariantFieldConfigDto(FieldDto primary, FieldDto secondary) {
        public record FieldDto(String label, String inputType, List<String> options,
                                Integer min, Integer max, boolean allowMultiple, boolean allowCustom) {}
    }
}
