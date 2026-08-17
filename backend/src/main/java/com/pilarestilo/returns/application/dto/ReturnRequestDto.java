package com.pilarestilo.returns.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReturnRequestDto(
        UUID id,
        UUID orderId,
        String kind,
        String status,
        String reason,
        UUID requestedBy,
        Instant requestedAt,
        /** The legal limit to return the money: requestedAt + 45 days. */
        Instant deadlineAt,
        long daysUntilDeadline,
        Instant resolvedAt,
        String resolutionNote,
        String itemDisposition,
        Instant dispositionAt,
        String dispositionNote,
        BigDecimal refundAmount,
        String refundCurrency,
        String refundMethod,
        String refundReference,
        boolean refundFileAttached,
        Instant refundedAt,
        /*
         * Never the account number, encrypted or otherwise. What identifies the account is enough
         * to recognise it, and after the refund settles the number is erased anyway.
         */
        String refundAccountHolder,
        String refundBankName,
        String refundAccountType,
        String refundAccountLast4,
        boolean refundAccountConfigured,
        UUID creditNoteId
) {}
