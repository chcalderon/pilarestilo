package com.pilarestilo.payment.infrastructure.web.requests;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PaymentGatewayWebhookRequest(
        @NotNull UUID paymentId,
        String gatewayReference,
        String gatewayStatus
) {}

