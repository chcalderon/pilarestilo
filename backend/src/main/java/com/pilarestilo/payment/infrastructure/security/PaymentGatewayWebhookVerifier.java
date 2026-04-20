package com.pilarestilo.payment.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class PaymentGatewayWebhookVerifier {

    private final String webhookSecret;

    public PaymentGatewayWebhookVerifier(
            @Value("${app.payment.gateway.webhook-secret:}") String webhookSecret
    ) {
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
    }

    public boolean isValid(String providedSignature) {
        if (webhookSecret.isBlank()) {
            return true;
        }
        if (providedSignature == null || providedSignature.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                providedSignature.trim().getBytes(StandardCharsets.UTF_8)
        );
    }
}

