package com.pilarestilo.notificationservice.infrastructure.adapters;

import com.pilarestilo.notificationservice.domain.model.NotificationMessage;
import com.pilarestilo.notificationservice.domain.model.NotificationRecipient;
import com.pilarestilo.notificationservice.domain.ports.MessagingSettingsPort;
import com.pilarestilo.notificationservice.domain.ports.NotificationSender;
import com.pilarestilo.notificationservice.domain.view.MessagingSettings;
import com.pilarestilo.notificationservice.metrics.NotificationMetrics;
import com.pilarestilo.notificationservice.shared.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SendGridEmailNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SendGridEmailNotificationSender.class);
    private static final String CHANNEL = "EMAIL_SENDGRID";

    private final RestClient.Builder restClientBuilder;
    private final MessagingSettingsPort messagingSettings;
    private final SystemSettingsCryptoService cryptoService;
    private final NotificationMetrics metrics;
    private final String envApiBaseUrl;
    private final String envApiKey;
    private final String envFromEmail;
    private final String envSenderName;
    private final String envFallbackTo;

    public SendGridEmailNotificationSender(
            RestClient.Builder restClientBuilder,
            MessagingSettingsPort messagingSettings,
            SystemSettingsCryptoService cryptoService,
            NotificationMetrics metrics,
            @Value("${app.notification.email.sendgrid.api-base-url:https://api.sendgrid.com}") String apiBaseUrl,
            @Value("${app.notification.email.sendgrid.api-key:}") String apiKey,
            @Value("${app.notification.email.sendgrid.from-email:}") String fromEmail,
            @Value("${app.notification.email.sendgrid.sender-name:Pilar Estilo}") String senderName,
            @Value("${app.notification.email.sendgrid.to-fallback:}") String fallbackTo
    ) {
        this.restClientBuilder = restClientBuilder;
        this.messagingSettings = messagingSettings;
        this.cryptoService = cryptoService;
        this.metrics = metrics;
        this.envApiBaseUrl = normalize(apiBaseUrl, "https://api.sendgrid.com");
        this.envApiKey = normalize(apiKey, "");
        this.envFromEmail = normalize(fromEmail, "");
        this.envSenderName = normalize(senderName, "Pilar Estilo");
        this.envFallbackTo = normalize(fallbackTo, "");
    }

    @SuppressWarnings("java:S2629")
    private void send(String template, UUID referenceId, NotificationRecipient recipient,
                      String subject, String body) {
        if (!recipient.allowsEmail()) {
            log.info("[EMAIL:SENDGRID] skipped template={} referenceId={} reason=channel-preference preference={}",
                    template, referenceId, recipient.preference());
            return;
        }

        EffectiveConfig config = resolveConfig();
        if (config == null) {
            return;
        }

        String toEmail = resolveToEmail(recipient, config.fallbackTo());
        if (toEmail == null) {
            log.warn("[EMAIL:SENDGRID] skipped template={} referenceId={} reason=no-valid-recipient recipient={}",
                    template, referenceId, recipient.preferredEmailThenPhone());
            return;
        }

        Map<String, Object> payload = Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", toEmail)))),
                "from", Map.of("email", config.fromEmail(), "name", config.senderName()),
                "subject", subject,
                "content", List.of(Map.of("type", "text/plain", "value", body)));

        try {
            RestClient restClient = restClientBuilder.baseUrl(config.apiBaseUrl()).build();
            restClient.post()
                    .uri("/v3/mail/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + config.apiKey())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[EMAIL:SENDGRID] template={} to={} referenceId={}", template, toEmail, referenceId);
        } catch (Exception ex) {
            metrics.countSendFailure(CHANNEL);
            log.warn("[EMAIL:SENDGRID] send failed template={} to={} referenceId={} reason={}",
                    template, toEmail, referenceId, ex.getMessage());
        }
    }

    @SuppressWarnings("java:S2259")
    private EffectiveConfig resolveConfig() {
        MessagingSettings settings = messagingSettings.current();
        String decryptedApiKey = decryptSecret(settings.sendgridApiKeyEncrypted(), "SendGrid API key");
        String apiBaseUrl = firstNonBlank(settings.sendgridApiBaseUrl(), envApiBaseUrl);
        String apiKey = firstNonBlank(decryptedApiKey, envApiKey);
        String fromEmail = firstNonBlank(settings.sendgridFromEmail(), envFromEmail);
        String senderName = firstNonBlank(settings.sendgridSenderName(), envSenderName);
        String fallbackTo = firstNonBlank(settings.sendgridToFallback(), envFallbackTo);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[EMAIL:SENDGRID] disabled: missing API key.");
            return null;
        }
        if (!EmailFormat.looksLikeEmail(fromEmail)) {
            log.warn("[EMAIL:SENDGRID] disabled: invalid sender email.");
            return null;
        }
        if (fallbackTo != null && !EmailFormat.looksLikeEmail(fallbackTo)) {
            fallbackTo = null;
        }

        return new EffectiveConfig(
                normalize(apiBaseUrl, "https://api.sendgrid.com"),
                apiKey.trim(), fromEmail.trim(),
                normalize(senderName, "Pilar Estilo"),
                fallbackTo == null ? null : fallbackTo.trim());
    }

    private String resolveToEmail(NotificationRecipient recipient, String fallbackTo) {
        if (EmailFormat.looksLikeEmail(recipient.email())) {
            return recipient.email().trim();
        }
        if (EmailFormat.looksLikeEmail(recipient.phone())) {
            return recipient.phone().trim();
        }
        if (EmailFormat.looksLikeEmail(fallbackTo)) {
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

    @Override
    public void send(NotificationMessage message, NotificationRecipient recipient) {
        send(message.templateKey(), message.referenceId(), recipient,
                message.subject(), message.bodyText());
    }
}
