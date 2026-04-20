package com.pilarestilo.payment.infrastructure.web.requests;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RegisterPaymentRequest(
        @NotNull(message = "orderId is required")
        UUID orderId,

        @NotNull(message = "paymentMethod is required")
        String paymentMethod
) {}
