package com.pilarestilo.order.application.commands;

import com.pilarestilo.order.domain.enums.DeliveryMethod;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A sale made off-platform (Instagram / Facebook / WhatsApp, later POS / MercadoLibre), recorded
 * from the admin panel. Payment has already been received; the order is born PAID.
 */
public record RegisterExternalSaleCommand(
        String idempotencyKey,
        String buyerName,
        String buyerContact,
        SalesChannel salesChannel,
        PaymentMethod paymentMethod,
        DeliveryMethod deliveryMethod,
        /** Free text. Required iff deliveryMethod == SHIPPING. */
        String shippingAddress,
        String notes,
        List<Line> items
) {
    public record Line(
            UUID productId,
            String variantColor,
            String variantSize,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
