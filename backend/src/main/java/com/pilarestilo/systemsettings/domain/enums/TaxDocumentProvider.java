package com.pilarestilo.systemsettings.domain.enums;

import java.util.Locale;

/**
 * Who produces the shop's tax documents.
 *
 * <p>{@link #MANUAL} is what the shop does today: the boleta is issued by hand in the SII's eBoleta
 * app and its folio is typed into the CMS. The other two name the providers that were evaluated —
 * TUU is free while card payments run through it, OpenFactura costs a yearly fee — and exist so the
 * choice lives in settings rather than in a deploy. Neither has an adapter yet; nothing reads this
 * value to route emission.
 */
public enum TaxDocumentProvider {
    MANUAL,
    TUU,
    OPENFACTURA;

    public static TaxDocumentProvider fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return MANUAL;
        }
        return TaxDocumentProvider.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
