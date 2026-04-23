package com.pilarestilo.systemsettings.domain.enums;

public enum PaymentGatewayProvider {
    MERCADO_PAGO;

    public static PaymentGatewayProvider fromRaw(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Payment gateway provider cannot be blank");
        }
        return PaymentGatewayProvider.valueOf(rawValue.trim().toUpperCase());
    }
}
