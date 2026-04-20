package com.pilarestilo.payment.domain.ports;

import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.shared.application.Money;

import java.time.Instant;
import java.util.UUID;

public interface PaymentGatewayPort {

    record CheckoutSession(
            String gatewayReference,
            String checkoutUrl,
            Instant expiresAt
    ) {}

    CheckoutSession initiatePayment(UUID orderId, Money amount);

    PaymentStatus checkStatus(String gatewayReference);
}
