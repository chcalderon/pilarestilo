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
        boolean employeeDiscountEligible,
        String salesChannel,
        /*
         * Provenance for discountAmount, resolved by the monolith, which owns the discount
         * catalogue and the redemption ledger. Stored, never interpreted: this service does not
         * validate the code and cannot redeem one.
         */
        UUID discountId,
        String discountCode,
        /*
         * The shop's configured VAT rate, resolved by the monolith, which owns system settings.
         * Only the rate travels: the total is computed here, so a net and a tax computed there
         * could disagree with it. Null falls back to TaxBreakdown.DEFAULT_RATE.
         */
        BigDecimal taxRate
) {
    /**
     * A copy with the caller's identity substituted.
     *
     * <p>The controller used to rebuild this record field by field to override customerId, which
     * silently dropped anything added later — the same shape of defect that once lost
     * discountCode on the way out of the monolith. Enumerating the fields once, here, means a new
     * field cannot go missing at a call site that never mentioned it.
     */
    public CreateOrderRequest withCustomerId(UUID effectiveCustomerId) {
        return new CreateOrderRequest(
                effectiveCustomerId,
                items,
                paymentMethod,
                shippingZoneCode,
                shippingCourierId,
                shippingAddressId,
                notes,
                discountAmount,
                discountCurrency,
                employeeDiscountEligible,
                salesChannel,
                discountId,
                discountCode,
                taxRate
        );
    }

    public record OrderItemRequest(
            UUID productId,
            int quantity,
            String variantColor,
            String variantSize
    ) {
    }
}
