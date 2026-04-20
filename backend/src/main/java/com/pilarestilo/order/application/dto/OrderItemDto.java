package com.pilarestilo.order.application.dto;

import java.util.UUID;

public record OrderItemDto(
        UUID id,
        UUID productId,
        String productName,
        MoneyDto unitPrice,
        int quantity
) {}
