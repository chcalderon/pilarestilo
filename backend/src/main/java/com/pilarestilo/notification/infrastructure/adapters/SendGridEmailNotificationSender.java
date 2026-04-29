package com.pilarestilo.notification.infrastructure.adapters;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class SendGridEmailNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SendGridEmailNotificationSender.class);

    private final RestClient.Builder restClientBuilder;
    private final SystemSettingsRepository systemSettingsRepository;
    private final SystemSettingsCryptoService cryptoService;
    private final String envApiBaseUrl;
    private final String envApiKey;
    private final String envFromEmail;
    private final String envSenderName;
    private final String envFallbackTo;

    public SendGridEmailNotificationSender(
            RestClient.Builder restClientBuilder,
            SystemSettingsRepository systemSettingsRepository,
            SystemSettingsCryptoService cryptoService,
            @Value("${app.notification.email.sendgrid.api-base-url:https://api.sendgrid.com}") String apiBaseUrl,
            @Value("${app.notification.email.sendgrid.api-key:}") String apiKey,
            @Value("${app.notification.email.sendgrid.from-email:}") String fromEmail,
            @Value("${app.notification.email.sendgrid.sender-name:Pilar Estilo}") String senderName,
            @Value("${app.notification.email.sendgrid.to-fallback:}") String fallbackTo
    ) {
        this.restClientBuilder = restClientBuilder;
        this.systemSettingsRepository = systemSettingsRepository;
        this.cryptoService = cryptoService;
        this.envApiBaseUrl = normalize(apiBaseUrl, "https://api.sendgrid.com");
        this.envApiKey = normalize(apiKey, "");
        this.envFromEmail = normalize(fromEmail, "");
        this.envSenderName = normalize(senderName, "Pilar Estilo");
        this.envFallbackTo = normalize(fallbackTo, "");
    }

    @Override
    public void sendOrderConfirmation(UUID orderId, NotificationRecipient recipient) {
        String subject = String.format(Locale.ROOT, "Pedido %s confirmado", shortId(orderId));
        String body = String.format(
                Locale.ROOT,
                "Tu pedido %s fue creado correctamente.%nTe notificaremos por este canal cuando avance.",
                orderId
        );
        send("ORDER_CONFIRMATION", orderId, recipient, subject, body);
    }

    @Override
    public void sendPaymentReceived(UUID paymentId, NotificationRecipient recipient) {
        String subject = String.format(Locale.ROOT, "Pago %s recibido", shortId(paymentId));
        String body = String.format(
                Locale.ROOT,
                "Confirmamos el pago %s.%nGracias por tu compra en Pilar Estilo.",
                paymentId
        );
        send("PAYMENT_RECEIVED", paymentId, recipient, subject, body);
    }

    @Override
    public void sendOrderPreparing(UUID orderId, NotificationRecipient recipient) {
        String subject = String.format(Locale.ROOT, "Pedido %s en preparacion", shortId(orderId));
        String body = String.format(
                Locale.ROOT,
                "Tu pedido %s esta en preparacion.%nTe avisaremos cuando sea despachado.",
                orderId
        );
        send("ORDER_PREPARING", orderId, recipient, subject, body);
    }

    @Override
    public void sendOrderShipped(UUID orderId, NotificationRecipient recipient) {
        String subject = String.format(Locale.ROOT, "Pedido %s enviado", shortId(orderId));
        String body = String.format(
                Locale.ROOT,
                "Tu pedido %s ya fue enviado.%nPronto llegara a destino.",
                orderId
        );
        send("ORDER_SHIPPED", orderId, recipient, subject, body);
    }

    @Override
    public void sendDiscountCodeAssigned(String code, NotificationRecipient recipient) {
        String subject = "Código de descuento exclusivo para ti";
        String body = String.format(Locale.ROOT,
            "Tienes un código de descuento exclusivo: %s%nÚsalo en tu próxima compra en Pilar Estilo.", code);
        send("DISCOUNT_CODE_ASSIGNED", null, recipient, subject, body);
    }

    private void send(
            String template,
            UUID referenceId,
            NotificationRecipient recipient,
            String subject,
            String body
    ) {
        if (!recipient.allowsEmail()) {
            log.info(
                    "[EMAIL:SENDGRID] skipped template={} referenceId={} reason=channel-preference preference={}",
                    template,
                    referenceId,
                    recipient.preference()
            );
            return;
        }

        EffectiveConfig config = resolveConfig();
        if (config == null) {
            return;
        }

        String toEmail = resolveToEmail(recipient, config.fallbackTo());
        if (toEmail == null) {
            log.warn(
                    "[EMAIL:SENDGRID] skipped template={} referenceId={} reason=no-valid-recipient recipient={}",
                    template,
                    referenceId,
                    recipient.preferredEmailThenPhone()
            );
            return;
        }

        Map<String, Object> payload = Map.of(
                "personalizations",
                List.of(Map.of("to", List.of(Map.of("email", toEmail)))),
                "from",
                Map.of("email", config.fromEmail(), "name", config.senderName()),
                "subject",
                subject,
                "content",
                List.of(Map.of("type", "text/plain", "value", body))
        );

        try {
            RestClient restClient = restClientBuilder
                    .baseUrl(config.apiBaseUrl())
                    .build();

            restClient.post()
                    .uri("/v3/mail/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + config.apiKey())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info(
                    "[EMAIL:SENDGRID] template={} to={} referenceId={}",
                    template,
                    toEmail,
                    referenceId
            );
        } catch (Exception ex) {
            log.warn(
                    "[EMAIL:SENDGRID] send failed template={} to={} referenceId={} reason={}",
                    template,
                    toEmail,
                    referenceId,
                    ex.getMessage()
            );
        }
    }

    private EffectiveConfig resolveConfig() {
        var settings = systemSettingsRepository.get();
        String decryptedApiKey = decryptSecret(settings.getSendgridApiKeyEncrypted(), "SendGrid API key");
        String apiBaseUrl = firstNonBlank(settings.getSendgridApiBaseUrl(), envApiBaseUrl);
        String apiKey = firstNonBlank(decryptedApiKey, envApiKey);
        String fromEmail = firstNonBlank(settings.getSendgridFromEmail(), envFromEmail);
        String senderName = firstNonBlank(settings.getSendgridSenderName(), envSenderName);
        String fallbackTo = firstNonBlank(settings.getSendgridToFallback(), envFallbackTo);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[EMAIL:SENDGRID] disabled: missing API key.");
            return null;
        }
        if (!looksLikeEmail(fromEmail)) {
            log.warn("[EMAIL:SENDGRID] disabled: invalid sender email.");
            return null;
        }
        if (fallbackTo != null && !looksLikeEmail(fallbackTo)) {
            fallbackTo = null;
        }

        return new EffectiveConfig(
                normalize(apiBaseUrl, "https://api.sendgrid.com"),
                apiKey.trim(),
                fromEmail.trim(),
                normalize(senderName, "Pilar Estilo"),
                fallbackTo == null ? null : fallbackTo.trim()
        );
    }

    private String resolveToEmail(NotificationRecipient recipient, String fallbackTo) {
        if (looksLikeEmail(recipient.email())) {
            return recipient.email().trim();
        }
        if (looksLikeEmail(recipient.phone())) {
            return recipient.phone().trim();
        }
        if (looksLikeEmail(fallbackTo)) {
            return fallbackTo.trim();
        }
        return null;
    }

    private String decryptSecret(String encrypted, String label) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        try {
            String decrypted = cryptoService.decrypt(encrypted);
            if (decrypted == null || decrypted.isBlank()) {
                return null;
            }
            return decrypted.trim();
        } catch (DomainException ex) {
            log.warn("[EMAIL:SENDGRID] could not decrypt {}: {}", label, ex.getMessage());
            return null;
        }
    }

    private String shortId(UUID id) {
        String raw = String.valueOf(id);
        return raw.length() >= 8 ? raw.substring(0, 8) : raw;
    }

    private boolean looksLikeEmail(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private record EffectiveConfig(
            String apiBaseUrl,
            String apiKey,
            String fromEmail,
            String senderName,
            String fallbackTo
    ) {}
}
