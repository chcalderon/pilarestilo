package com.pilarestilo.discount.infrastructure.persistence.entities;

import com.pilarestilo.discount.domain.enums.RedemptionStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "discount_code_usages")
public class DiscountCodeUsageEntity {

    @Id
    private UUID id;

    @Column(name = "discount_id", nullable = false)
    private UUID discountId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Order that reserved this redemption. Null for rows written before V67. */
    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RedemptionStatus status;

    /** When the redemption was reserved. Column name kept from V30. */
    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    public DiscountCodeUsageEntity() {}

    public DiscountCodeUsageEntity(UUID discountId, UUID userId, UUID orderId) {
        this.id = UUID.randomUUID();
        this.discountId = discountId;
        this.userId = userId;
        this.orderId = orderId;
        this.status = RedemptionStatus.PENDING;
        this.usedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getDiscountId() { return discountId; }
    public UUID getUserId() { return userId; }
    public UUID getOrderId() { return orderId; }
    public RedemptionStatus getStatus() { return status; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getSettledAt() { return settledAt; }
    public Instant getReleasedAt() { return releasedAt; }

    // No setters for status/settledAt/releasedAt on purpose. Settling and releasing go through
    // the conditional UPDATEs in DiscountCodeUsageJpaRepository, whose `WHERE status = 'PENDING'`
    // is what makes both idempotent. Mutating the entity here would bypass that guard.
}
