package com.pilarestilo.cashregister.infrastructure.web.requests;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CloseCashRegisterRequest(@NotNull BigDecimal closingBalance, String notes) {}
