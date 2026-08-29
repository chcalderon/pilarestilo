package com.pilarestilo.notificationservice.domain.view;

import java.time.Instant;
import java.util.UUID;

/** Read-only projection of {@code payments}: the bank-transfer snapshot and the review outcome. */
public record PaymentView(
        UUID id,
        UUID orderId,
        String method,
        String status,
        String rejectionReason,
        String proofReference,
        Instant createdAt,
        String transferAccountHolderName,
        String transferBankName,
        String transferAccountType,
        String transferAccountNumber,
        String transferAccountEmail) {

    public boolean isTransfer() {
        return "TRANSFER".equals(method);
    }
}
