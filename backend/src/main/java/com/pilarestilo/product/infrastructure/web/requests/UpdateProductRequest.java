package com.pilarestilo.product.infrastructure.web.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UpdateProductRequest(
        @NotBlank(message = "Name is required")
        String name,

        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        BigDecimal priceAmount,

        String priceCurrency,

        @DecimalMin(value = "0.01", message = "List price must be greater than zero")
        BigDecimal listPriceAmount,

        String listPriceCurrency,

        String imageUrl,

        @NotBlank(message = "Condition is required")
        String condition,

        @NotBlank(message = "Brand is required")
        String brand,

        @Min(value = 0, message = "Stock cannot be negative")
        int stock,

        boolean active,

        Set<UUID> categoryIds,

        /** Null or blank leaves it derived from the categories, as it was before V69. */
        String variantType,

        List<@Valid ProductVariantRequest> variants
) {}
