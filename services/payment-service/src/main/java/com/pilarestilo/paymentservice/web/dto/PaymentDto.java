package com.pilarestilo.paymentservice.web.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentDto(
        UUID id,
        UUID orderId,
        String method,
        String status,
        String proofReference,
        String transferAccountHolderName,
        String transferAccountEmail,
        String transferAccountNumber,
        String transferAccountType,
        UUID reviewedBy,
        Instant reviewedAt,
        Instant createdAt
) {
}
