package com.pilarestilo.order.infrastructure.web.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record OrderItemRequest(
        @NotNull(message = "productId is required")
        UUID productId,

        @Positive(message = "quantity must be positive")
        int quantity,

        String variantColor,
        String variantSize
) {}
