package com.pilarestilo.discount.infrastructure.persistence.repositories;

import com.pilarestilo.discount.domain.ports.DiscountRedemptionRepository;
import com.pilarestilo.discount.infrastructure.persistence.entities.DiscountCodeUsageEntity;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public class DiscountRedemptionRepositoryAdapter implements DiscountRedemptionRepository {

    private final DiscountCodeUsageJpaRepository usageRepository;
    private final DiscountJpaRepository discountRepository;

    public DiscountRedemptionRepositoryAdapter(DiscountCodeUsageJpaRepository usageRepository,
                                               DiscountJpaRepository discountRepository) {
        this.usageRepository = usageRepository;
        this.discountRepository = discountRepository;
    }

    @Override
    public boolean hasActiveRedemption(UUID discountId, UUID userId) {
        return usageRepository.existsActiveForUser(discountId, userId);
    }

    /**
     * Claims the usage slot first, then writes the ledger row.
     *
     * <p>That order matters. The conditional UPDATE is the concurrency guard — it fails when the
     * code is exhausted, so two customers racing for the last use of a single-use code produce
     * exactly one winner without any lock. Writing the ledger row first would let both through and
     * leave the counter to sort it out afterwards.
     */
    @Override
    @Transactional
    public UUID reserve(UUID discountId, UUID userId, UUID orderId) {
        if (discountRepository.atomicClaimUsage(discountId) == 0) {
            throw new DomainException("Discount usage limit reached");
        }
        try {
            return usageRepository.save(new DiscountCodeUsageEntity(discountId, userId, orderId)).getId();
        } catch (DataIntegrityViolationException _) {
            // uq_dcu_active: the user already holds a live redemption for this code.
            // uq_dcu_order: this order already reserved one.
            // Both mean the claim above was not ours to keep; the surrounding transaction rolls
            // it back, so no compensating decrement is needed.
            throw new DomainException("Ya usaste este código de descuento");
        }
    }

    @Override
    @Transactional
    public boolean settle(UUID orderId) {
        return usageRepository.settleForOrder(orderId) > 0;
    }

    /**
     * Frees the slot only when a PENDING row was actually flipped. The counter decrement is
     * therefore driven by rows-affected rather than by a prior read, which removes the
     * read-then-write window that would otherwise let a double release drop it twice.
     */
    @Override
    @Transactional
    public boolean release(UUID orderId) {
        UUID discountId = usageRepository.findDiscountIdByOrderId(orderId);
        if (discountId == null) {
            return false;
        }
        if (usageRepository.releaseForOrder(orderId) == 0) {
            return false;
        }
        discountRepository.atomicReleaseUsage(discountId);
        return true;
    }
}
