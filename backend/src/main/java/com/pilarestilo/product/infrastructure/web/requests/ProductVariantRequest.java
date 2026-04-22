package com.pilarestilo.product.infrastructure.web.requests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ProductVariantRequest(
        @NotBlank(message = "Variant color is required")
        String color,

        @NotBlank(message = "Variant size is required")
        String size,

        @Min(value = 0, message = "Variant stock cannot be negative")
        int stock
) {}
