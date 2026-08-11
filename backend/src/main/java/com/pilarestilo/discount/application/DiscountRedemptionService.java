package com.pilarestilo.discount.application;

import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.domain.ports.DiscountRedemptionRepository;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.application.Money;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The single place where "may this user redeem this code?" is answered.
 *
 * <p>Those rules used to live only in {@code ValidateDiscountForUserUseCase}, which backs the
 * storefront's "apply code" button. {@code CreateOrderUseCase} re-implemented a thinner version
 * that checked neither ownership of an assigned code nor prior use, so posting straight to
 * {@code POST /api/orders} redeemed a code assigned to somebody else. Reuse was stopped only by a
 * unique constraint surfacing as an opaque database error.
 *
 * <p>Redemption is reserve-then-settle: {@link #reserve} records a PENDING row when the order is
 * created, {@link #settle} finalises it when the order is paid, {@link #release} returns the slot
 * when the order is cancelled.
 */
@Service
public class DiscountRedemptionService {

    private final DiscountRepository discountRepository;
    private final DiscountRedemptionRepository redemptionRepository;

    public DiscountRedemptionService(DiscountRepository discountRepository,
                                     DiscountRedemptionRepository redemptionRepository) {
        this.discountRepository = discountRepository;
        this.redemptionRepository = redemptionRepository;
    }

    /**
     * A validated code together with the amount it takes off this subtotal. Carrying the discount
     * itself means the caller never has to look it up again between validating and reserving.
     */
    public record DiscountEvaluation(Discount discount, Money amount) {
        public UUID discountId() {
            return discount.getId();
        }

        public String code() {
            return discount.getCode();
        }
    }

    /**
     * Runs every guard and returns the resulting amount. Read-only: nothing is consumed here, so
     * this is safe to call from the storefront on every keystroke of the coupon field.
     */
    public DiscountEvaluation evaluate(String rawCode, Money subtotal, UUID userId) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new DomainException("Código de descuento no encontrado");
        }

        Discount discount = discountRepository.findByCode(rawCode.trim().toUpperCase())
                .orElseThrow(() -> new DomainException("Código de descuento no encontrado"));

        // active, within its date window, under maxUses, and subtotal above the minimum
        discount.validate(subtotal);

        if (discount.getAssignedUserId() != null && !discount.getAssignedUserId().equals(userId)) {
            throw new DomainException("Este código no está disponible para tu cuenta");
        }

        if (redemptionRepository.hasActiveRedemption(discount.getId(), userId)) {
            throw new DomainException("Ya usaste este código de descuento");
        }

        return new DiscountEvaluation(discount, discount.computeDiscountFor(subtotal));
    }

    /**
     * Consumes a usage slot for an order that has just been saved.
     *
     * <p>Must run after the order row exists — {@code discount_code_usages.order_id} references it.
     * Losing a capacity race here throws, rolling back the whole order transaction, which is the
     * correct outcome: the customer was quoted a price that is no longer available.
     */
    public void reserve(DiscountEvaluation evaluation, UUID userId, UUID orderId) {
        redemptionRepository.reserve(evaluation.discountId(), userId, orderId);
    }

    /** Called when an order reaches PAID. Idempotent. */
    public boolean settle(UUID orderId) {
        return redemptionRepository.settle(orderId);
    }

    /**
     * Called when an order is cancelled, whether by an admin or by the bank-transfer auto-cancel
     * job. Only PENDING redemptions are released, so cancelling an already-paid order does not
     * hand the code back. Idempotent.
     */
    public boolean release(UUID orderId) {
        return redemptionRepository.release(orderId);
    }
}
