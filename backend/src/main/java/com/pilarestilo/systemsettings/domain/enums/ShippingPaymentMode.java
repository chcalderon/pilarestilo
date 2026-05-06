package com.pilarestilo.systemsettings.domain.enums;

import java.util.Locale;

public enum ShippingPaymentMode {
    POR_PAGAR;

    public static ShippingPaymentMode fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return POR_PAGAR;
        }
        return ShippingPaymentMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
