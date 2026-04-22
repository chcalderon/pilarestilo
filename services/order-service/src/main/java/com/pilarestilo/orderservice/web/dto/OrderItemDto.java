package com.pilarestilo.orderservice.web.dto;

import java.util.UUID;

public record OrderItemDto(
        UUID id,
        UUID productId,
        String productName,
        MoneyDto unitPrice,
        int quantity
) {
}
