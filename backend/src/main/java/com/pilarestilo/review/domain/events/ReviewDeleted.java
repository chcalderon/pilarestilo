package com.pilarestilo.review.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ReviewDeleted(UUID reviewId, UUID productId, Instant occurredAt) implements DomainEvent {

    public ReviewDeleted(UUID reviewId, UUID productId) {
        this(reviewId, productId, Instant.now());
    }
}
