package com.pilarestilo.orderservice.web.dto;

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
        String paymentMethod,
        String shippingZoneCode,
        String shippingCourierId,
        String shippingCourierName,
        String shippingPaymentMode,
        UUID shippingAddressId,
        String shippingAddressReference,
        String notes,
        String salesChannel,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
