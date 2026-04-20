package com.pilarestilo.discount.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DiscountApplied(
        UUID discountId,
        String discountCode,
        UUID orderId,
        BigDecimal discountAmount,
        Instant occurredAt
) implements DomainEvent {

    public DiscountApplied(UUID discountId, String discountCode, UUID orderId, BigDecimal discountAmount) {
        this(discountId, discountCode, orderId, discountAmount, Instant.now());
    }
}
