package com.pilarestilo.returns.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** A customer retracted, or the shop opened a return by agreement. */
public record ReturnRequested(
        UUID returnId,
        UUID orderId,
        String kind,
        Instant occurredAt
) implements DomainEvent {
}
