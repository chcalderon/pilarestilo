package com.pilarestilo.discount.infrastructure.persistence.entities;

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

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    public DiscountCodeUsageEntity() {}

    public DiscountCodeUsageEntity(UUID discountId, UUID userId) {
        this.id = UUID.randomUUID();
        this.discountId = discountId;
        this.userId = userId;
        this.usedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getDiscountId() { return discountId; }
    public UUID getUserId() { return userId; }
    public Instant getUsedAt() { return usedAt; }
}
