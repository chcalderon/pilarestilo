package com.pilarestilo.order.application.dto;

import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDto(
        UUID id,
        UUID customerId,
        List<OrderItemDto> items,
        MoneyDto subtotal,
        MoneyDto discountAmount,
        MoneyDto totalAmount,
        PaymentMethod paymentMethod,
        String notes,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
