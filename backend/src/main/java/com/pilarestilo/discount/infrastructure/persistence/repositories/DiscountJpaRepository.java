package com.pilarestilo.discount.infrastructure.persistence.repositories;

import com.pilarestilo.discount.infrastructure.persistence.entities.DiscountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscountJpaRepository extends JpaRepository<DiscountEntity, UUID> {

    Optional<DiscountEntity> findByCode(String code);

    /**
     * Claims one usage slot. Returns 0 when the discount is exhausted, which is the concurrency
     * guard: two customers redeeming the last use of a code race on this single statement and
     * exactly one wins. Mirrors ProductJpaRepository.atomicReserveVariantStock rather than taking
     * a pessimistic lock.
     */
    @Modifying
    @Query(value = """
        UPDATE discounts
           SET times_used = times_used + 1
         WHERE id = :discountId AND times_used < max_uses
        """, nativeQuery = true)
    int atomicClaimUsage(@Param("discountId") UUID discountId);

    /** Returns a slot on release. GREATEST floors at zero so a stray call can never go negative. */
    @Modifying
    @Query(value = """
        UPDATE discounts
           SET times_used = GREATEST(times_used - 1, 0)
         WHERE id = :discountId
        """, nativeQuery = true)
    int atomicReleaseUsage(@Param("discountId") UUID discountId);

    /**
     * "Vigente" means still redeemable: on, within its date window, and with a slot left. A
     * single-use code (the shape of every welcome coupon) that has already been claimed is not
     * vigente just because {@code validUntil} has not arrived yet.
     */
    @Query("SELECT d FROM DiscountEntity d WHERE d.active = true AND d.validUntil >= :today "
            + "AND d.timesUsed < d.maxUses ORDER BY d.validUntil ASC")
    List<DiscountEntity> findActiveDiscounts(@Param("today") LocalDate today);

    @Query("SELECT d FROM DiscountEntity d WHERE d.active = false OR d.validUntil < :today "
            + "OR d.timesUsed >= d.maxUses ORDER BY d.validUntil DESC")
    List<DiscountEntity> findExpiredDiscounts(@Param("today") LocalDate today);

    @Query("SELECT COUNT(d) FROM DiscountEntity d WHERE d.code LIKE :pattern")
    long countByCodeLike(@Param("pattern") String pattern);
}
