package com.pilarestilo.order.application.commands;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.shared.application.Money;

import java.util.List;
import java.util.UUID;

public record CreateOrderCommand(
        UUID customerId,
        List<OrderItemCommand> items,
        PaymentMethod paymentMethod,
        String shippingZoneCode,
        String shippingCourierId,
        String shippingAddressReference,
        String notes,
        Money discountAmount,
        boolean employeeDiscountEligible,
        String discountCode
) {
    public CreateOrderCommand(UUID customerId, List<OrderItemCommand> items,
                               PaymentMethod paymentMethod,
                               String shippingZoneCode,
                               String shippingCourierId,
                               String shippingAddressReference,
                               String notes,
                               Money discountAmount, boolean employeeDiscountEligible) {
        this(
                customerId,
                items,
                paymentMethod,
                shippingZoneCode,
                shippingCourierId,
                shippingAddressReference,
                notes,
                discountAmount,
                employeeDiscountEligible,
                null
        );
    }

    public record OrderItemCommand(
            UUID productId,
            int quantity,
            String variantColor,
            String variantSize
    ) {}
}
