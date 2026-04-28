package com.pilarestilo.cashregister.infrastructure.web.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record OpenCashRegisterRequest(@NotNull @Positive BigDecimal openingBalance) {}
