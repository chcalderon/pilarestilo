package com.pilarestilo.notificationservice.domain.view;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-only projection of {@code sales_documents}. {@code type} is the raw column value
 * ({@code BOLETA} / {@code FACTURA} / {@code NOTA_CREDITO}); the composer branches on it.
 */
public record SalesDocumentView(
        UUID id,
        String type,
        String folio,
        Money net,
        Money tax,
        BigDecimal taxRate,
        Money total) {

    public boolean isCreditNote() {
        return "NOTA_CREDITO".equals(type);
    }
}
