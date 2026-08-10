package com.pilarestilo.discount.domain.enums;

/**
 * Lifecycle of a discount redemption.
 *
 * <p>PENDING occupies a usage slot without being final: the order exists but has not been paid.
 * Reaching PAID settles it; cancelling the order releases it so the customer can use the code
 * again. Only PENDING redemptions are released — cancelling an order that was already paid is a
 * refund, and returning a single-use code for goods that may have shipped is a leak.
 */
public enum RedemptionStatus {
    PENDING,
    SETTLED,
    RELEASED
}
