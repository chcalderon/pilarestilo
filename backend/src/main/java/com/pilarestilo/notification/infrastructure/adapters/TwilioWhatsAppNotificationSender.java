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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Locale;
import java.util.UUID;

@Component
public class TwilioWhatsAppNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppNotificationSender.class);

    private final RestClient.Builder restClientBuilder;
    private final SystemSettingsRepository systemSettingsRepository;
    private final SystemSettingsCryptoService cryptoService;
    private final String envApiBaseUrl;
    private final String envAccountSid;
    private final String envAuthToken;
    private final String envFrom;
    private final String envToFallback;
    private final String envSenderAlias;

    public TwilioWhatsAppNotificationSender(
            RestClient.Builder restClientBuilder,
            SystemSettingsRepository systemSettingsRepository,
            SystemSettingsCryptoService cryptoService,
            @Value("${app.notification.whatsapp.twilio.api-base-url:https://api.twilio.com}") String apiBaseUrl,
            @Value("${app.notification.whatsapp.twilio.account-sid:}") String accountSid,
            @Value("${app.notification.whatsapp.twilio.auth-token:}") String authToken,
            @Value("${app.notification.whatsapp.twilio.from:}") String from,
            @Value("${app.notification.whatsapp.twilio.to-fallback:-+56900000000}") String toFallback,
            @Value("${app.notification.whatsapp.twilio.sender-alias:Pilar Estilo}") String senderAlias
    ) {
        this.restClientBuilder = restClientBuilder;
        this.systemSettingsRepository = systemSettingsRepository;
        this.cryptoService = cryptoService;
        this.envApiBaseUrl = normalize(apiBaseUrl, "https://api.twilio.com");
        this.envAccountSid = normalize(accountSid, "");
        this.envAuthToken = normalize(authToken, "");
        this.envFrom = normalize(from, "");
        this.envToFallback = normalize(toFallback, "+56900000000");
        this.envSenderAlias = normalize(senderAlias, "Pilar Estilo");
    }

    @Override
    public void sendOrderConfirmation(UUID orderId, NotificationRecipient recipient) {
        send("ORDER_CONFIRMATION", orderId, recipient);
    }

    @Override
    public void sendPaymentReceived(UUID paymentId, NotificationRecipient recipient) {
        send("PAYMENT_RECEIVED", paymentId, recipient);
    }

    @Override
    public void sendOrderShipped(UUID orderId, NotificationRecipient recipient) {
        send("ORDER_SHIPPED", orderId, recipient);
    }

    private void send(String template, UUID referenceId, NotificationRecipient recipient) {
        if (!recipient.allowsWhatsApp()) {
            log.info(
                    "[WHATSAPP:TWILIO] skipped template={} referenceId={} reason=channel-preference preference={}",
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

        String body = buildMessage(template, referenceId, config.senderAlias());
        String recipientContact = normalize(recipient.preferredPhoneThenEmail(), "unknown");
        String toAddress = resolveToAddress(recipientContact, config.fallbackToAddress());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", toAddress);
        form.add("From", config.fromAddress());
        form.add("Body", body);

        try {
            RestClient restClient = restClientBuilder
                    .baseUrl(config.apiBaseUrl())
                    .defaultHeaders(headers -> headers.setBasicAuth(config.accountSid(), config.authToken()))
                    .build();

            restClient.post()
                    .uri("/2010-04-01/Accounts/{sid}/Messages.json", config.accountSid())
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

    private String buildMessage(String template, UUID referenceId, String senderAlias) {
        return switch (template) {
            case "ORDER_CONFIRMATION" -> String.format(
                    Locale.ROOT,
                    "%s: pedido %s creado. Te avisaremos cuando avance.",
                    senderAlias,
                    shortId(referenceId)
            );
            case "PAYMENT_RECEIVED" -> String.format(
                    Locale.ROOT,
                    "%s: pago %s confirmado. Gracias por tu compra.",
                    senderAlias,
                    shortId(referenceId)
            );
            default -> String.format(
                    Locale.ROOT,
                    "%s: pedido %s enviado. Pronto llegara a destino.",
                    senderAlias,
                    shortId(referenceId)
            );
        };
    }

    private EffectiveConfig resolveConfig() {
        var settings = systemSettingsRepository.get();
        String apiBaseUrl = firstNonBlank(settings.getWhatsappTwilioApiBaseUrl(), envApiBaseUrl);
        String accountSid = firstNonBlank(settings.getWhatsappTwilioAccountSid(), envAccountSid);
        String decryptedToken = decryptSecret(settings.getWhatsappTwilioAuthTokenEncrypted(), "Twilio auth token");
        String authToken = firstNonBlank(decryptedToken, envAuthToken);
        String fromAddress = normalizeWhatsappAddress(firstNonBlank(settings.getWhatsappTwilioFrom(), envFrom));
        String fallbackToAddress = normalizeWhatsappAddress(firstNonBlank(settings.getWhatsappTwilioToFallback(), envToFallback));
        String senderAlias = normalize(firstNonBlank(settings.getWhatsappTwilioSenderAlias(), envSenderAlias), "Pilar Estilo");

        if (accountSid == null || accountSid.isBlank()) {
            log.warn("[WHATSAPP:TWILIO] disabled: missing account SID.");
            return null;
        }
        if (authToken == null || authToken.isBlank()) {
            log.warn("[WHATSAPP:TWILIO] disabled: missing auth token.");
            return null;
        }
        if (fromAddress == null) {
            log.warn("[WHATSAPP:TWILIO] disabled: missing/invalid 'from' number.");
            return null;
        }
        if (fallbackToAddress == null) {
            log.warn("[WHATSAPP:TWILIO] disabled: missing/invalid fallback number.");
            return null;
        }

        return new EffectiveConfig(
                normalize(apiBaseUrl, "https://api.twilio.com"),
                accountSid.trim(),
                authToken.trim(),
                fromAddress,
                fallbackToAddress,
                senderAlias
        );
    }

    private String resolveToAddress(String recipientContact, String fallbackToAddress) {
        String normalized = normalizeWhatsappAddress(recipientContact);
        if (normalized != null) {
            return normalized;
        }
        return fallbackToAddress;
    }

    private String normalizeWhatsappAddress(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.regionMatches(true, 0, "whatsapp:", 0, "whatsapp:".length())) {
            candidate = candidate.substring("whatsapp:".length());
        }
        String normalizedPhone = normalizePhone(candidate);
        if (normalizedPhone == null) {
            return null;
        }
        return "whatsapp:" + normalizedPhone;
    }

    private String normalizePhone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 8 || digits.length() > 15) {
            return null;
        }
        return "+" + digits;
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
            log.warn("[WHATSAPP:TWILIO] could not decrypt {}: {}", label, ex.getMessage());
            return null;
        }
    }

    private String shortId(UUID id) {
        String raw = String.valueOf(id);
        return raw.length() >= 8 ? raw.substring(0, 8) : raw;
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
            String accountSid,
            String authToken,
            String fromAddress,
            String fallbackToAddress,
            String senderAlias
    ) {}
}
