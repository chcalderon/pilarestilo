package com.pilarestilo.product.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ProductCreated(UUID productId, String productName, Instant occurredAt) implements DomainEvent {

    public ProductCreated(UUID productId, String productName) {
        this(productId, productName, Instant.now());
    }
}
