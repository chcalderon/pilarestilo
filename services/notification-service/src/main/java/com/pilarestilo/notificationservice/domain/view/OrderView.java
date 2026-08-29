package com.pilarestilo.notificationservice.domain.view;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * What a dispatcher and {@code NotificationComposer} need to know about an order to write to the
 * customer. Read-only projection of {@code orders} + {@code order_items} on the shared database.
 */
public record OrderView(
        UUID id,
        String publicReference,
        UUID customerId,
        String status,
        Money subtotal,
        Money discount,
        Money net,
        Money tax,
        BigDecimal taxRate,
        Money total,
        String shippingCourierId,
        String shippingCourierName,
        String shippingZoneCode,
        List<OrderItemView> items) {

    public record OrderItemView(
            String productName,
            String variantColor,
            String variantSize,
            int quantity,
            Money unitPrice) {
    }
}
