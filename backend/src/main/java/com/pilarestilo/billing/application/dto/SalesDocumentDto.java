package com.pilarestilo.billing.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SalesDocumentDto(
        UUID id,
        UUID orderId,
        String documentType,
        String folio,
        Instant issuedAt,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal taxRate,
        BigDecimal totalAmount,
        String currency,
        String receiverRut,
        String receiverName,
        String receiverEmail,
        /** True when a file was uploaded. The file itself is served by an authenticated endpoint. */
        boolean fileAttached,
        String status,
        Instant voidedAt,
        String voidReason,
        UUID replacesDocumentId,
        /** Only on a NOTA_CREDITO: 1 annuls the referenced document, 2 corrects text, 3 an amount. */
        Integer referenceCode,
        UUID issuedBy
) {}
