package com.pilarestilo.payment.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReviewPaymentRequest(
        @NotBlank(message = "action is required (APPROVE or REJECT)")
        String action,

        @NotNull(message = "reviewerId is required")
        UUID reviewerId
) {}
