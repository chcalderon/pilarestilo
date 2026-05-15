package com.pilarestilo.payment.application.dto;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.domain.enums.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record PaymentDto(
        UUID id,
        UUID orderId,
        PaymentMethod method,
        PaymentStatus status,
        String proofReference,
        String transferAccountHolderName,
        String transferAccountEmail,
        String transferAccountNumber,
        String transferBankName,
        String transferAccountType,
        UUID reviewedBy,
        Instant reviewedAt,
        Instant createdAt,
        String rejectionReason
) {}
