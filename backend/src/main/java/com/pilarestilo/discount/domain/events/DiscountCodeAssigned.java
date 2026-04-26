package com.pilarestilo.discount.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record DiscountCodeAssigned(
    UUID discountId,
    String code,
    UUID assignedUserId,
    Instant occurredAt
) implements DomainEvent {

    public DiscountCodeAssigned(UUID discountId, String code, UUID assignedUserId) {
        this(discountId, code, assignedUserId, Instant.now());
    }
}
