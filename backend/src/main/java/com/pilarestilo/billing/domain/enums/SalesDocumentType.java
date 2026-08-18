package com.pilarestilo.billing.domain.enums;

/**
 * The kind of tax document backing a sale.
 *
 * <p>{@code BOLETA} is DTE 39 and does not require the buyer's RUT. {@code FACTURA} is DTE 33 and
 * does, along with the buyer's razon social and giro; the table carries the columns but no screen
 * asks for them yet.
 *
 * <p>{@code NOTA_CREDITO} is DTE 61, and it is a type rather than a status for a reason. Voiding is
 * a status flip, which is honest only while the document has not reached the SII. A boleta for a
 * delivered sale has been declared: undoing it takes a credit note with its own folio that
 * references it. The credit note does not erase the boleta, it counterweighs it, and both stay
 * valid — which is why a credit note is the one document allowed to live alongside another.
 */
public enum SalesDocumentType {
    BOLETA,
    FACTURA,
    NOTA_CREDITO;

    /** A document that declares a sale, as opposed to one that undoes it. */
    public boolean isSale() {
        return this != NOTA_CREDITO;
    }
}
