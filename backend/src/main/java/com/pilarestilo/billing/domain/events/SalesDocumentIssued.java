package com.pilarestilo.billing.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record SalesDocumentIssued(
        UUID documentId,
        UUID orderId,
        String folio,
        Instant occurredAt
) implements DomainEvent {
}
