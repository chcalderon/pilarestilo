package com.pilarestilo.notificationservice.domain.view;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection of {@code return_requests}. {@code kind} is the raw column value
 * ({@code RETRACTO} / {@code DEVOLUCION}); {@code refund} is null until a refund is registered.
 */
public record ReturnView(
        UUID id,
        UUID orderId,
        String kind,
        String reason,
        Instant deadlineAt,
        Money refund,
        String refundMethod,
        String refundReference,
        Instant refundedAt) {

    public boolean isRetracto() {
        return "RETRACTO".equals(kind);
    }
}
