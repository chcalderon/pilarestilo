package com.pilarestilo.payment.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PaymentConfirmed(
        UUID paymentId,
        UUID orderId,
        Instant occurredAt
) implements DomainEvent {}
