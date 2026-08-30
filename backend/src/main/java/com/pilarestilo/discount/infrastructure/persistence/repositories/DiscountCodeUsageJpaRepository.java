package com.pilarestilo.discount.infrastructure.persistence.repositories;

import com.pilarestilo.discount.infrastructure.persistence.entities.DiscountCodeUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DiscountCodeUsageJpaRepository extends JpaRepository<DiscountCodeUsageEntity, UUID> {

    /** A released redemption no longer blocks the user, so it must not count as active. */
    @Query(value = """
        SELECT EXISTS (SELECT 1 FROM discount_code_usages
                        WHERE discount_id = :discountId
                          AND user_id = :userId
                          AND status <> 'RELEASED')
        """, nativeQuery = true)
    boolean existsActiveForUser(@Param("discountId") UUID discountId, @Param("userId") UUID userId);

    /**
     * Settles the order's pending redemption. The usage slot stays occupied, so times_used is
     * untouched.
     *
     * <p>{@code status = 'PENDING'} in the predicate is what makes this idempotent: a second call
     * matches nothing. Both the payment saga and the admin status endpoint can reach it.
     */
    @Modifying
    @Query(value = """
        UPDATE discount_code_usages
           SET status = 'SETTLED', settled_at = NOW()
         WHERE order_id = :orderId AND status = 'PENDING'
        """, nativeQuery = true)
    int settleForOrder(@Param("orderId") UUID orderId);

    /** Releases the order's pending redemption. Same idempotency guard as settle. */
    @Modifying
    @Query(value = """
        UPDATE discount_code_usages
           SET status = 'RELEASED', released_at = NOW()
         WHERE order_id = :orderId AND status = 'PENDING'
        """, nativeQuery = true)
    int releaseForOrder(@Param("orderId") UUID orderId);

    @Query(value = "SELECT discount_id FROM discount_code_usages WHERE order_id = :orderId",
           nativeQuery = true)
    UUID findDiscountIdByOrderId(@Param("orderId") UUID orderId);
}
