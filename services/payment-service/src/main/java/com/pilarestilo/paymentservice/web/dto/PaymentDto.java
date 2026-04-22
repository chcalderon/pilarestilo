package com.pilarestilo.paymentservice.web.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentDto(
        UUID id,
        UUID orderId,
        String method,
        String status,
        String proofReference,
        UUID reviewedBy,
        Instant reviewedAt,
        Instant createdAt
) {
}
