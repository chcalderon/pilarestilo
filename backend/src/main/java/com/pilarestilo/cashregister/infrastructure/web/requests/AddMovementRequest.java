package com.pilarestilo.cashregister.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record AddMovementRequest(
        @NotNull String type,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String description
) {}
