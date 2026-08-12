package com.pilarestilo.orderservice.web.dto;

import java.util.UUID;

public record OrderItemDto(
        UUID id,
        UUID productId,
        String productName,
        MoneyDto unitPrice,
        int quantity,
        /*
         * Matches the monolith's OrderItemDto. Without these the caller cannot tell which
         * variant an order line refers to, which is how the monolith ended up releasing stock
         * against the wrong record when an order was cancelled.
         */
        String variantColor,
        String variantSize
) {
}
