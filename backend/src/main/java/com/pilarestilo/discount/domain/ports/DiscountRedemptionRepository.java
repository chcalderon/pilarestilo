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
     * Records a PENDING redemption and consumes one usage slot.
     *
     * <p>{@code orderId} may be null. Order writes delegated to order-service have no order row
     * to point at yet, so those reserve first — claiming the slot before anything else happens —
     * and call {@link #attachOrder} once the remote order exists. Claiming first means losing a
     * capacity race costs nothing, because no order has been created to compensate for.
     *
     * @return the id of the ledger row, needed to attach the order afterwards.
     * @throws com.pilarestilo.shared.domain.DomainException when the discount ran out of uses
     *         between validation and this call, or the user already holds an active redemption.
     */
    UUID reserve(UUID discountId, UUID userId, UUID orderId);

    /**
     * Points a reservation made without an order at the order that now exists.
     *
     * <p>Without it the row could never be settled or released, both of which look up by order id.
     *
     * @throws com.pilarestilo.shared.domain.DomainException when the row is gone or already bound.
     */
    void attachOrder(UUID redemptionId, UUID orderId);

    /** Marks the order's PENDING redemption SETTLED. No-op if there is none. */
    boolean settle(UUID orderId);

    /** Releases the order's PENDING redemption and frees its usage slot. No-op if there is none. */
    boolean release(UUID orderId);
}
