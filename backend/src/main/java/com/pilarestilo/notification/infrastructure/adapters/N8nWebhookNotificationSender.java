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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class N8nWebhookNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(N8nWebhookNotificationSender.class);
    private static final String DEFAULT_TOKEN_HEADER_NAME = "X-PE-N8N-TOKEN";

    private final RestClient.Builder restClientBuilder;
    private final SystemSettingsRepository systemSettingsRepository;
    private final SystemSettingsCryptoService cryptoService;
    private final String envWebhookUrl;
    private final String envApiKey;
    private final String envTokenHeaderName;

    public N8nWebhookNotificationSender(
            RestClient.Builder restClientBuilder,
            SystemSettingsRepository systemSettingsRepository,
            SystemSettingsCryptoService cryptoService,
            @Value("${app.notification.n8n.webhook-url:}") String webhookUrl,
            @Value("${app.notification.n8n.api-key:}") String apiKey,
            @Value("${app.notification.n8n.token-header-name:X-PE-N8N-TOKEN}") String tokenHeaderName
    ) {
        this.restClientBuilder = restClientBuilder;
        this.systemSettingsRepository = systemSettingsRepository;
        this.cryptoService = cryptoService;
        this.envWebhookUrl = normalize(webhookUrl);
        this.envApiKey = normalize(apiKey);
        this.envTokenHeaderName = normalize(tokenHeaderName);
    }

    @Override
    public void sendOrderConfirmation(UUID orderId, NotificationRecipient recipient) {
        sendWebhook("ORDER_CONFIRMATION", orderId, recipient);
    }

    @Override
    public void sendPaymentReceived(UUID paymentId, NotificationRecipient recipient) {
        sendWebhook("PAYMENT_RECEIVED", paymentId, recipient);
    }

    @Override
    public void sendOrderPreparing(UUID orderId, NotificationRecipient recipient) {
        sendWebhook("ORDER_PREPARING", orderId, recipient);
    }

    @Override
    public void sendOrderShipped(UUID orderId, NotificationRecipient recipient) {
        sendWebhook("ORDER_SHIPPED", orderId, recipient);
    }

    @Override
    public void sendDiscountCodeAssigned(String code, NotificationRecipient recipient) {
        sendWebhookCode("DISCOUNT_CODE_ASSIGNED", code, recipient);
    }

    private void sendWebhookCode(String eventType, String reference, NotificationRecipient recipient) {
        EffectiveConfig config = resolveConfig();
        if (config == null) {
            log.warn("[NOTIFICATION:N8N] disabled: missing webhook URL.");
            return;
        }
        Map<String, Object> recipientPayload = new LinkedHashMap<>();
        recipientPayload.put("phone", recipient.phone());
        recipientPayload.put("email", recipient.email());
        recipientPayload.put("channelPreference", recipient.preference().name());
        recipientPayload.put("allowWhatsApp", recipient.allowsWhatsApp());
        recipientPayload.put("allowEmail", recipient.allowsEmail());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("reference", reference);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("recipient", recipientPayload);

        try {
            RestClient.RequestBodySpec request = restClientBuilder.build()
                .post().uri(config.webhookUrl()).contentType(MediaType.APPLICATION_JSON);
            if (!config.apiKey().isBlank()) {
                request = request.header(config.tokenHeaderName(), config.apiKey());
            }
            request.body(payload).retrieve().toBodilessEntity();
            log.info("[NOTIFICATION:N8N] event={} reference={}", eventType, reference);
        } catch (Exception ex) {
            log.warn("[NOTIFICATION:N8N] send failed event={} reference={} reason={}", eventType, reference, ex.getMessage());
        }
    }

    private void sendWebhook(String eventType, UUID referenceId, NotificationRecipient recipient) {
        EffectiveConfig config = resolveConfig();
        if (config == null) {
            log.warn("[NOTIFICATION:N8N] disabled: missing webhook URL.");
            return;
        }

        Map<String, Object> recipientPayload = new LinkedHashMap<>();
        recipientPayload.put("phone", recipient.phone());
        recipientPayload.put("email", recipient.email());
        recipientPayload.put("channelPreference", recipient.preference().name());
        recipientPayload.put("allowWhatsApp", recipient.allowsWhatsApp());
        recipientPayload.put("allowEmail", recipient.allowsEmail());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("referenceId", referenceId.toString());
        payload.put("occurredAt", Instant.now().toString());
        payload.put("recipient", recipientPayload);

        try {
            RestClient.RequestBodySpec request = restClientBuilder
                    .build()
                    .post()
                    .uri(config.webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON);

            if (!config.apiKey().isBlank()) {
                request = request.header(config.tokenHeaderName(), config.apiKey());
            }

            request.body(payload).retrieve().toBodilessEntity();
            log.info(
                    "[NOTIFICATION:N8N] event={} referenceId={} recipient={} preference={}",
                    eventType,
                    referenceId,
                    recipient.preferredEmailThenPhone(),
                    recipient.preference()
            );
        } catch (Exception ex) {
            log.warn(
                    "[NOTIFICATION:N8N] send failed event={} referenceId={} reason={}",
                    eventType,
                    referenceId,
                    ex.getMessage()
            );
        }
    }

    private EffectiveConfig resolveConfig() {
        var settings = systemSettingsRepository.get();
        String webhookUrl = firstNonBlank(settings.getN8nWebhookUrl(), envWebhookUrl);
        String decryptedApiKey = decryptSecret(settings.getN8nApiKeyEncrypted(), "n8n api key");
        String apiKey = firstNonBlank(decryptedApiKey, envApiKey);
        String tokenHeaderName = normalizeHeaderName(firstNonBlank(settings.getN8nTokenHeaderName(), envTokenHeaderName));

        if (webhookUrl == null || webhookUrl.isBlank()) {
            return null;
        }

        return new EffectiveConfig(
                webhookUrl,
                normalize(apiKey),
                tokenHeaderName
        );
    }

    private String decryptSecret(String encryptedValue, String label) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            return null;
        }
        try {
            String decrypted = cryptoService.decrypt(encryptedValue);
            if (decrypted == null || decrypted.isBlank()) {
                return null;
            }
            return decrypted.trim();
        } catch (DomainException ex) {
            log.warn("[NOTIFICATION:N8N] could not decrypt {}: {}", label, ex.getMessage());
            return null;
        }
    }

    private String normalizeHeaderName(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_TOKEN_HEADER_NAME;
        }
        return value.trim();
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

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record EffectiveConfig(
            String webhookUrl,
            String apiKey,
            String tokenHeaderName
    ) {}
}
