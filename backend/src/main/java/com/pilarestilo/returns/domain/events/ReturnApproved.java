package com.pilarestilo.returns.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** The return was accepted; the garment can be sent back. */
public record ReturnApproved(
        UUID returnId,
        UUID orderId,
        Instant occurredAt
) implements DomainEvent {
}
