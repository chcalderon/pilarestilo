package com.pilarestilo.orderservice.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        UUID customerId,
        List<OrderItemRequest> items,
        String paymentMethod,
        String shippingZoneCode,
        String shippingCourierId,
        UUID shippingAddressId,
        String notes,
        BigDecimal discountAmount,
        String discountCurrency,
        boolean employeeDiscountEligible
) {
    public record OrderItemRequest(
            UUID productId,
            int quantity,
            String variantColor,
            String variantSize
    ) {
    }
}
