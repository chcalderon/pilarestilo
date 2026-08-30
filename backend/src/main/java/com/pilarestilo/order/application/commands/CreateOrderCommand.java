package com.pilarestilo.order.application.commands;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;
import com.pilarestilo.shared.application.Money;

import java.util.List;
import java.util.UUID;

public record CreateOrderCommand(
        UUID customerId,
        List<OrderItemCommand> items,
        PaymentMethod paymentMethod,
        String shippingZoneCode,
        String shippingCourierId,
        UUID shippingAddressId,
        String notes,
        Money discountAmount,
        boolean employeeDiscountEligible,
        String discountCode,
        SalesChannel salesChannel
) {
    public CreateOrderCommand(UUID customerId, List<OrderItemCommand> items,
                               PaymentMethod paymentMethod,
                               String shippingZoneCode,
                               String shippingCourierId,
                               UUID shippingAddressId,
                               String notes,
                               Money discountAmount, boolean employeeDiscountEligible) {
        this(
                customerId,
                items,
                paymentMethod,
                shippingZoneCode,
                shippingCourierId,
                shippingAddressId,
                notes,
                discountAmount,
                employeeDiscountEligible,
                null,
                SalesChannel.ECOMMERCE
        );
    }

    public CreateOrderCommand(UUID customerId, List<OrderItemCommand> items,
                               PaymentMethod paymentMethod,
                               String shippingZoneCode,
                               String shippingCourierId,
                               UUID shippingAddressId,
                               String notes,
                               Money discountAmount, boolean employeeDiscountEligible,
                               String discountCode) {
        this(
                customerId,
                items,
                paymentMethod,
                shippingZoneCode,
                shippingCourierId,
                shippingAddressId,
                notes,
                discountAmount,
                employeeDiscountEligible,
                discountCode,
                SalesChannel.ECOMMERCE
        );
    }

    public record OrderItemCommand(
            UUID productId,
            int quantity,
            String variantColor,
            String variantSize
    ) {}
}
