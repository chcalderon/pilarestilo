package com.pilarestilo.discount.domain.ports;

import java.util.UUID;

/**
 * Redemption ledger. Kept separate from {@link DiscountRepository}, which is about the discount
 * aggregate itself.
 *
 * <p>Implementations must make {@code settle} and {@code release} idempotent: both are reached
 * from more than one path (the payment saga and the admin status endpoint) and may be called for
 * an order that never had a discount.
 */
public interface DiscountRedemptionRepository {

    /** True when the user holds a PENDING or SETTLED redemption for this discount. */
    boolean hasActiveRedemption(UUID discountId, UUID userId);

    /**
     * Records a PENDING redemption for a saved order and consumes one usage slot.
     *
     * @return the id of the ledger row.
     * @throws com.pilarestilo.shared.domain.DomainException when the discount ran out of uses
     *         between validation and this call, or the user already holds an active redemption.
     */
    UUID reserve(UUID discountId, UUID userId, UUID orderId);

    /** Marks the order's PENDING redemption SETTLED. No-op if there is none. */
    boolean settle(UUID orderId);

    /** Releases the order's PENDING redemption and frees its usage slot. No-op if there is none. */
    boolean release(UUID orderId);
}
