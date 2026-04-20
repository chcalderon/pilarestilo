package com.pilarestilo.review.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ReviewCreated(UUID reviewId, UUID productId, UUID userId, int rating, Instant occurredAt)
        implements DomainEvent {

    public ReviewCreated(UUID reviewId, UUID productId, UUID userId, int rating) {
        this(reviewId, productId, userId, rating, Instant.now());
    }
}
