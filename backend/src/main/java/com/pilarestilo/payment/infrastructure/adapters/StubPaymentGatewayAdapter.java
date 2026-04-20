package com.pilarestilo.payment.infrastructure.adapters;

import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.domain.ports.PaymentGatewayPort;
import com.pilarestilo.shared.application.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Component
public class StubPaymentGatewayAdapter implements PaymentGatewayPort {

    private final String checkoutBaseUrl;

    public StubPaymentGatewayAdapter(
            @Value("${app.payment.gateway.stub-checkout-base-url:https://payments.pilarestilo.local/checkout}") String checkoutBaseUrl
    ) {
        this.checkoutBaseUrl = checkoutBaseUrl;
    }

    @Override
    public CheckoutSession initiatePayment(UUID orderId, Money amount) {
        String reference = "stub-" + orderId.toString().substring(0, 8) + "-" + UUID.randomUUID().toString().substring(0, 8);
        String checkoutUrl = checkoutBaseUrl + "?ref=" + reference;
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(30));
        return new CheckoutSession(reference, checkoutUrl, expiresAt);
    }

    @Override
    public PaymentStatus checkStatus(String gatewayReference) {
        String normalized = gatewayReference == null ? "" : gatewayReference.toLowerCase(Locale.ROOT);
        if (normalized.contains("approved") || normalized.contains("paid") || normalized.contains("success")) {
            return PaymentStatus.APPROVED;
        }
        if (normalized.contains("rejected") || normalized.contains("failed") || normalized.contains("cancelled")) {
            return PaymentStatus.REJECTED;
        }
        return PaymentStatus.PENDING;
    }
}
