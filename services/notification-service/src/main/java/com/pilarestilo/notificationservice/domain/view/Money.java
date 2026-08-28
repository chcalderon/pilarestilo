package com.pilarestilo.notificationservice.domain.view;

import java.math.BigDecimal;

/** Same shape as the monolith's {@code shared.application.Money}: an amount and its currency. */
public record Money(BigDecimal amount, String currency) {

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount == null ? BigDecimal.ZERO : amount, currency == null ? "CLP" : currency);
    }
}
