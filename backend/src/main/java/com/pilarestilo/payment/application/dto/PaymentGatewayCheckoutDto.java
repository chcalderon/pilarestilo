package com.pilarestilo.payment.application.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentGatewayCheckoutDto(
        UUID paymentId,
        UUID orderId,
        String gatewayReference,
        String checkoutUrl,
        Instant expiresAt
) {}

