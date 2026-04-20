package com.pilarestilo.order.domain.events;

import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record OrderStatusChanged(
        UUID orderId,
        UUID customerId,
        OrderStatus previousStatus,
        OrderStatus newStatus,
        Instant occurredAt
) implements DomainEvent {}
