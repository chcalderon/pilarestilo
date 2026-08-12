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
        SalesChannel salesChannel,
        /**
         * Set only on the delegated-write path, where the monolith resolves the code and
         * order-service persists the provenance it cannot look up itself.
         */
        UUID resolvedDiscountId
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
                SalesChannel.ECOMMERCE,
                null
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
                SalesChannel.ECOMMERCE,
                null
        );
    }

    /**
     * The full command as the storefront sends it: no provenance, because the code has not been
     * resolved to a discount yet. CreateOrderUseCase fills that in for the delegated-write path.
     */
    public CreateOrderCommand(UUID customerId, List<OrderItemCommand> items,
                               PaymentMethod paymentMethod,
                               String shippingZoneCode,
                               String shippingCourierId,
                               UUID shippingAddressId,
                               String notes,
                               Money discountAmount, boolean employeeDiscountEligible,
                               String discountCode, SalesChannel salesChannel) {
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
                salesChannel,
                null
        );
    }

    /**
     * A copy carrying the discount the monolith resolved.
     *
     * <p>For the delegated-write path. order-service is told the amount and the code that
     * produced it, but never asked to redeem anything: the ledger lives here.
     */
    public CreateOrderCommand withResolvedDiscount(Money amount, UUID discountId, String code) {
        return new CreateOrderCommand(
                customerId,
                items,
                paymentMethod,
                shippingZoneCode,
                shippingCourierId,
                shippingAddressId,
                notes,
                amount,
                employeeDiscountEligible,
                code,
                salesChannel,
                discountId
        );
    }

    public record OrderItemCommand(
            UUID productId,
            int quantity,
            String variantColor,
            String variantSize
    ) {}
}
