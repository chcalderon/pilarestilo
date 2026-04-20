package com.pilarestilo.inventory.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record StockUpdated(
        UUID productId,
        int newStock,
        Instant occurredAt
) implements DomainEvent {}
