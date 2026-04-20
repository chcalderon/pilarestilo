package com.pilarestilo.payment.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PaymentRejected(
        UUID paymentId,
        UUID orderId,
        UUID reviewerId,
        Instant occurredAt
) implements DomainEvent {}
