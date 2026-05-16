package com.pilarestilo.cashregister.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record AddMovementRequest(
        @NotNull String type,
        @NotNull String category,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String description
) {}
