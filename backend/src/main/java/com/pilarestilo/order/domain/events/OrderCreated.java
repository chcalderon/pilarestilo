package com.pilarestilo.order.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record OrderCreated(
        UUID orderId,
        UUID customerId,
        Instant occurredAt
) implements DomainEvent {}
