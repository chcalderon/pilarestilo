package com.pilarestilo.notification.infrastructure.adapters;

import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

@Component
public class SmtpEmailNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailNotificationSender.class);

    private final SystemSettingsRepository systemSettingsRepository;
    private final SystemSettingsCryptoService cryptoService;
    private final String envHost;
    private final String envPort;
    private final String envUsername;
    private final String envPassword;
    private final String envFromEmail;
    private final String envSenderName;
    private final String envAuthEnabled;
    private final String envStarttlsEnabled;
    private final String envSslEnabled;
    private final String envFallbackTo;

    public SmtpEmailNotificationSender(
            SystemSettingsRepository systemSettingsRepository,
            SystemSettingsCryptoService cryptoService,
            @Value("${app.notification.email.smtp.host:}") String envHost,
            @Value("${app.notification.email.smtp.port:}") String envPort,
            @Value("${app.notification.email.smtp.username:}") String envUsername,
            @Value("${app.notification.email.smtp.password:}") String envPassword,
            @Value("${app.notification.email.smtp.from-email:}") String envFromEmail,
            @Value("${app.notification.email.smtp.sender-name:Pilar Estilo}") String envSenderName,
            @Value("${app.notification.email.smtp.auth-enabled:}") String envAuthEnabled,
            @Value("${app.notification.email.smtp.starttls-enabled:}") String envStarttlsEnabled,
            @Value("${app.notification.email.smtp.ssl-enabled:}") String envSslEnabled,
            @Value("${app.notification.email.smtp.to-fallback:}") String envFallbackTo
    ) {
        this.systemSettingsRepository = systemSettingsRepository;
        this.cryptoService = cryptoService;
        this.envHost = normalize(envHost, "");
        this.envPort = normalize(envPort, "");
        this.envUsername = normalize(envUsername, "");
        this.envPassword = normalize(envPassword, "");
        this.envFromEmail = normalize(envFromEmail, "");
        this.envSenderName = normalize(envSenderName, "Pilar Estilo");
        this.envAuthEnabled = normalize(envAuthEnabled, "");
        this.envStarttlsEnabled = normalize(envStarttlsEnabled, "");
        this.envSslEnabled = normalize(envSslEnabled, "");
        this.envFallbackTo = normalize(envFallbackTo, "");
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
                    "[EMAIL:SMTP] skipped template={} referenceId={} reason=channel-preference preference={}",
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
                    "[EMAIL:SMTP] skipped template={} referenceId={} reason=no-valid-recipient recipient={}",
                    template,
                    referenceId,
                    recipient.preferredEmailThenPhone()
            );
            return;
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host());
        sender.setPort(config.port());
        if (config.username() != null && !config.username().isBlank()) {
            sender.setUsername(config.username());
        }
        if (config.password() != null && !config.password().isBlank()) {
            sender.setPassword(config.password());
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(config.authEnabled()));
        props.put("mail.smtp.starttls.enable", String.valueOf(config.starttlsEnabled()));
        props.put("mail.smtp.ssl.enable", String.valueOf(config.sslEnabled()));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(config.fromEmail(), config.senderName());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, false);
            sender.send(message);
            log.info("[EMAIL:SMTP] template={} to={} referenceId={}", template, toEmail, referenceId);
        } catch (Exception ex) {
            log.warn(
                    "[EMAIL:SMTP] send failed template={} to={} referenceId={} reason={}",
                    template,
                    toEmail,
                    referenceId,
                    ex.getMessage()
            );
        }
    }

    private EffectiveConfig resolveConfig() {
        var settings = systemSettingsRepository.get();
        String host = firstNonBlank(settings.getSmtpHost(), envHost);
        Integer port = parsePort(firstNonBlank(
                settings.getSmtpPort() == null ? null : String.valueOf(settings.getSmtpPort()),
                envPort
        ));
        String username = firstNonBlank(settings.getSmtpUsername(), envUsername);
        String decryptedPassword = decryptPassword(settings.getSmtpPasswordEncrypted());
        String password = firstNonBlank(decryptedPassword, envPassword);
        String fromEmail = firstNonBlank(settings.getSmtpFromEmail(), envFromEmail);
        String senderName = envSenderName;
        boolean authEnabled = parseBooleanOrDefault(envAuthEnabled, settings.isSmtpAuthEnabled());
        boolean starttlsEnabled = parseBooleanOrDefault(envStarttlsEnabled, settings.isSmtpStarttlsEnabled());
        boolean sslEnabled = parseBooleanOrDefault(envSslEnabled, port != null && port == 465);
        String fallbackTo = firstNonBlank(envFallbackTo, null);

        if (host == null || host.isBlank()) {
            log.warn("[EMAIL:SMTP] disabled: missing SMTP host (EMAIL_SMTP_HOST or smtpHost in admin settings).");
            return null;
        }
        if (port == null || port < 1 || port > 65535) {
            log.warn("[EMAIL:SMTP] disabled: missing/invalid SMTP port (EMAIL_SMTP_PORT or smtpPort in admin settings).");
            return null;
        }
        if (!looksLikeEmail(fromEmail)) {
            log.warn("[EMAIL:SMTP] disabled: invalid sender email (EMAIL_SMTP_FROM_EMAIL or smtpFromEmail in admin settings).");
            return null;
        }
        if (authEnabled && (username == null || username.isBlank() || password == null || password.isBlank())) {
            log.warn("[EMAIL:SMTP] disabled: SMTP auth enabled but username/password are not configured.");
            return null;
        }
        if (fallbackTo != null && !looksLikeEmail(fallbackTo)) {
            fallbackTo = null;
        }

        return new EffectiveConfig(
                host,
                port,
                username,
                password,
                fromEmail,
                senderName,
                authEnabled,
                starttlsEnabled,
                sslEnabled,
                fallbackTo
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

    private String decryptPassword(String encrypted) {
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
            log.warn("[EMAIL:SMTP] could not decrypt stored SMTP password: {}", ex.getMessage());
            return null;
        }
    }

    private Integer parsePort(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean parseBooleanOrDefault(String rawValue, boolean defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(rawValue.trim());
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
            String host,
            int port,
            String username,
            String password,
            String fromEmail,
            String senderName,
            boolean authEnabled,
            boolean starttlsEnabled,
            boolean sslEnabled,
            String fallbackTo
    ) {}

    @Override
    public void send(NotificationMessage message, NotificationRecipient recipient) {
        send(message.templateKey(), message.referenceId(), recipient,
                message.subject(), message.bodyText());
    }
}
