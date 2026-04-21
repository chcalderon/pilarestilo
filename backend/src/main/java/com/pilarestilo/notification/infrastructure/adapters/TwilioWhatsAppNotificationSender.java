package com.pilarestilo.notification.infrastructure.adapters;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Locale;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.notification.provider", havingValue = "WHATSAPP_TWILIO")
public class TwilioWhatsAppNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppNotificationSender.class);

    private final RestClient restClient;
    private final String accountSid;
    private final String fromAddress;
    private final String fallbackToAddress;
    private final String senderAlias;

    public TwilioWhatsAppNotificationSender(
            RestClient.Builder restClientBuilder,
            @Value("${app.notification.whatsapp.twilio.api-base-url:https://api.twilio.com}") String apiBaseUrl,
            @Value("${app.notification.whatsapp.twilio.account-sid:}") String accountSid,
            @Value("${app.notification.whatsapp.twilio.auth-token:}") String authToken,
            @Value("${app.notification.whatsapp.twilio.from:}") String from,
            @Value("${app.notification.whatsapp.twilio.to-fallback:-+56900000000}") String toFallback,
            @Value("${app.notification.whatsapp.twilio.sender-alias:Pilar Estilo}") String senderAlias
    ) {
        this.accountSid = requireNonBlank(accountSid, "WHATSAPP_TWILIO_ACCOUNT_SID");
        String normalizedToken = requireNonBlank(authToken, "WHATSAPP_TWILIO_AUTH_TOKEN");
        this.fromAddress = normalizeWhatsappAddress(
                requireNonBlank(from, "WHATSAPP_TWILIO_FROM"),
                "WHATSAPP_TWILIO_FROM"
        );
        this.fallbackToAddress = normalizeWhatsappAddress(
                normalize(toFallback, "+56900000000"),
                "WHATSAPP_TWILIO_TO_FALLBACK"
        );
        this.senderAlias = normalize(senderAlias, "Pilar Estilo");
        this.restClient = restClientBuilder
                .baseUrl(normalize(apiBaseUrl, "https://api.twilio.com"))
                .defaultHeaders(headers -> headers.setBasicAuth(this.accountSid, normalizedToken))
                .build();
    }

    @Override
    public void sendOrderConfirmation(UUID orderId, NotificationRecipient recipient) {
        String destination = normalizeRecipient(recipient.preferredPhoneThenEmail());
        String body = String.format(
                Locale.ROOT,
                "%s: pedido %s creado. Te avisaremos cuando avance.",
                senderAlias,
                shortId(orderId)
        );
        send("ORDER_CONFIRMATION", orderId, destination, body);
    }

    @Override
    public void sendPaymentReceived(UUID paymentId, NotificationRecipient recipient) {
        String destination = normalizeRecipient(recipient.preferredPhoneThenEmail());
        String body = String.format(
                Locale.ROOT,
                "%s: pago %s confirmado. Gracias por tu compra.",
                senderAlias,
                shortId(paymentId)
        );
        send("PAYMENT_RECEIVED", paymentId, destination, body);
    }

    @Override
    public void sendOrderShipped(UUID orderId, NotificationRecipient recipient) {
        String destination = normalizeRecipient(recipient.preferredPhoneThenEmail());
        String body = String.format(
                Locale.ROOT,
                "%s: pedido %s enviado. Pronto llegara a destino.",
                senderAlias,
                shortId(orderId)
        );
        send("ORDER_SHIPPED", orderId, destination, body);
    }

    private void send(String template, UUID referenceId, String recipientContact, String body) {
        String toAddress = resolveToAddress(recipientContact);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", toAddress);
        form.add("From", fromAddress);
        form.add("Body", body);

        try {
            restClient.post()
                    .uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            log.info(
                    "[WHATSAPP:TWILIO] template={} to={} recipient={} referenceId={}",
                    template,
                    toAddress,
                    recipientContact,
                    referenceId
            );
        } catch (Exception ex) {
            log.warn(
                    "[WHATSAPP:TWILIO] send failed template={} to={} recipient={} referenceId={} reason={}",
                    template,
                    toAddress,
                    recipientContact,
                    referenceId,
                    ex.getMessage()
            );
        }
    }

    private String resolveToAddress(String recipientContact) {
        if (looksLikePhone(recipientContact)) {
            return normalizeWhatsappAddress(recipientContact, "recipient");
        }
        return fallbackToAddress;
    }

    private boolean looksLikePhone(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String candidate = value.trim();
        if (candidate.regionMatches(true, 0, "whatsapp:", 0, "whatsapp:".length())) {
            candidate = candidate.substring("whatsapp:".length());
        }
        if (candidate.startsWith("+")) {
            candidate = candidate.substring(1);
        }
        return candidate.matches("\\d{8,15}");
    }

    private String normalizeWhatsappAddress(String value, String fieldName) {
        String candidate = normalize(value, "");
        if (candidate.regionMatches(true, 0, "whatsapp:", 0, "whatsapp:".length())) {
            String withoutPrefix = candidate.substring("whatsapp:".length()).trim();
            if (looksLikePhone(withoutPrefix)) {
                return "whatsapp:" + normalizePhone(withoutPrefix);
            }
            throw new IllegalStateException(fieldName + " must include a valid phone number");
        }
        if (looksLikePhone(candidate)) {
            return "whatsapp:" + normalizePhone(candidate);
        }
        throw new IllegalStateException(fieldName + " must include a valid phone number");
    }

    private String normalizePhone(String value) {
        String candidate = value.trim();
        if (!candidate.startsWith("+")) {
            return "+" + candidate.replaceAll("\\D", "");
        }
        return "+" + candidate.substring(1).replaceAll("\\D", "");
    }

    private String normalizeRecipient(String recipient) {
        return normalize(recipient, "unknown");
    }

    private String shortId(UUID id) {
        String raw = String.valueOf(id);
        return raw.length() >= 8 ? raw.substring(0, 8) : raw;
    }

    private String requireNonBlank(String value, String envName) {
        String normalized = normalize(value, "");
        if (normalized.isBlank()) {
            throw new IllegalStateException(envName + " is required when provider is WHATSAPP_TWILIO");
        }
        return normalized;
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
