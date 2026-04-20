package com.pilarestilo.payment.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PaymentSubmitted(
        UUID paymentId,
        String proofReference,
        Instant occurredAt
) implements DomainEvent {}
