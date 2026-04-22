package com.pilarestilo.orderservice.web.dto;

import java.math.BigDecimal;

public record MoneyDto(
        BigDecimal amount,
        String currency
) {
}
