package com.pilarestilo.order.application.dto;

import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDto(
        UUID id,
        /** Short code the customer quotes on a bank transfer; see OrderReference. */
        String publicReference,
        UUID customerId,
        List<OrderItemDto> items,
        MoneyDto subtotal,
        MoneyDto discountAmount,
        MoneyDto totalAmount,
        /** The total split as a boleta reports it: taxable base, VAT, and the rate applied. */
        MoneyDto netAmount,
        MoneyDto taxAmount,
        java.math.BigDecimal taxRate,
        PaymentMethod paymentMethod,
        String shippingZoneCode,
        String shippingCourierId,
        String shippingCourierName,
        String shippingPaymentMode,
        UUID shippingAddressId,
        String shippingAddressReference,
        String notes,
        SalesChannel salesChannel,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
