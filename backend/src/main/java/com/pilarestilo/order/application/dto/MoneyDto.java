package com.pilarestilo.order.application.dto;

import java.math.BigDecimal;

public record MoneyDto(BigDecimal amount, String currency) {}
