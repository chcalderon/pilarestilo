package com.pilarestilo.order.domain.enums;

import com.fasterxml.jackson.annotation.JsonAlias;

public enum PaymentMethod {
    @JsonAlias({"CASH_ON_DELIVERY"})
    CASH,
    DEBIT,
    CREDIT,
    @JsonAlias({"BANK_TRANSFER"})
    TRANSFER,
    @JsonAlias({"PAYMENT_GATEWAY"})
    WEBPAY,
    MERCADOPAGO,
    @JsonAlias({"AGREED_BY_WHATSAPP", "STORE_CREDIT"})
    OTHER
}
