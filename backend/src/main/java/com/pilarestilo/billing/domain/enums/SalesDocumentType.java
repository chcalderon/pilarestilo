package com.pilarestilo.billing.domain.enums;

/**
 * The kind of tax document backing a sale.
 *
 * <p>{@code BOLETA} is DTE 39 and does not require the buyer's RUT. {@code FACTURA} is DTE 33 and
 * does, along with the buyer's razon social and giro; the table carries the columns but no screen
 * asks for them yet. A credit note (DTE 61) is deliberately absent: voiding is modelled as a status
 * on the document, and a nota de credito is a different document with its own folio, so it will
 * arrive as its own constant when it arrives.
 */
public enum SalesDocumentType {
    BOLETA,
    FACTURA
}
