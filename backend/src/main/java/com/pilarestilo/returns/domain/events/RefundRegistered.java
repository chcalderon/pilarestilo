package com.pilarestilo.returns.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** The money went back. The garment may still be in reconditioning; the two tracks are separate. */
public record RefundRegistered(
        UUID returnId,
        UUID orderId,
        Instant occurredAt
) implements DomainEvent {
}
