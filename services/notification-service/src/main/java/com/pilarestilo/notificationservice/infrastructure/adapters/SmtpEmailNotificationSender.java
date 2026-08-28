package com.pilarestilo.notificationservice.infrastructure.adapters;

import com.pilarestilo.notificationservice.application.EmailLayout;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

@Component
public class SmtpEmailNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailNotificationSender.class);
    private static final String CHANNEL = "EMAIL_SMTP";

    private final MessagingSettingsPort messagingSettings;
    private final SystemSettingsCryptoService cryptoService;
    private final NotificationMetrics metrics;
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
            MessagingSettingsPort messagingSettings,
            SystemSettingsCryptoService cryptoService,
            NotificationMetrics metrics,
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
        this.messagingSettings = messagingSettings;
        this.cryptoService = cryptoService;
        this.metrics = metrics;
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

    @SuppressWarnings("java:S2629")
    private void send(
            String template,
            UUID referenceId,
            NotificationRecipient recipient,
            String subject,
            String body,
            String htmlBody
    ) {
        if (!recipient.allowsEmail()) {
            log.info("[EMAIL:SMTP] skipped template={} referenceId={} reason=channel-preference preference={}",
                    template, referenceId, recipient.preference());
            return;
        }

        EffectiveConfig config = resolveConfig();
        if (config == null) {
            return;
        }

        String toEmail = resolveToEmail(recipient, config.fallbackTo());
        if (toEmail == null) {
            log.warn("[EMAIL:SMTP] skipped template={} referenceId={} reason=no-valid-recipient recipient={}",
                    template, referenceId, recipient.preferredEmailThenPhone());
            return;
        }

        JavaMailSenderImpl sender = buildSender(config);

        try {
            var message = sender.createMimeMessage();
            boolean hasHtml = htmlBody != null && !htmlBody.isBlank();
            var helper = new MimeMessageHelper(message, hasHtml, StandardCharsets.UTF_8.name());
            helper.setFrom(config.fromEmail(), config.senderName());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            if (hasHtml) {
                helper.setText(body, htmlBody);
                attachLogo(helper);
            } else {
                helper.setText(body, false);
            }
            sender.send(message);
            log.info("[EMAIL:SMTP] template={} to={} referenceId={}", template, toEmail, referenceId);
        } catch (Exception ex) {
            metrics.countSendFailure(CHANNEL);
            log.warn("[EMAIL:SMTP] send failed template={} to={} referenceId={} reason={}",
                    template, toEmail, referenceId, ex.getMessage());
        }
    }

    private void attachLogo(MimeMessageHelper helper) {
        try {
            ClassPathResource logo = new ClassPathResource(EmailLayout.LOGO_RESOURCE);
            if (!logo.exists()) {
                log.warn("[EMAIL:SMTP] logo not on the classpath at {}", EmailLayout.LOGO_RESOURCE);
                return;
            }
            helper.addInline(EmailLayout.LOGO_CONTENT_ID, logo, "image/png");
        } catch (Exception ex) {
            log.warn("[EMAIL:SMTP] could not attach the logo: {}", ex.getMessage());
        }
    }

    /** Package-private seam for the test beside this class. */
    JavaMailSenderImpl buildSender(EffectiveConfig config) {
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
        return sender;
    }

    private EffectiveConfig resolveConfig() {
        MessagingSettings settings = messagingSettings.current();
        String host = firstNonBlank(settings.smtpHost(), envHost);
        Integer port = parsePort(firstNonBlank(
                settings.smtpPort() == null ? null : String.valueOf(settings.smtpPort()), envPort));
        String username = firstNonBlank(settings.smtpUsername(), envUsername);
        String decryptedPassword = decryptPassword(settings.smtpPasswordEncrypted());
        String password = firstNonBlank(decryptedPassword, envPassword);
        String fromEmail = firstNonBlank(settings.smtpFromEmail(), envFromEmail);
        String senderName = envSenderName;
        boolean authEnabled = parseBooleanOrDefault(envAuthEnabled, settings.smtpAuthEnabled());
        boolean starttlsEnabled = parseBooleanOrDefault(envStarttlsEnabled, settings.smtpStarttlsEnabled());
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
        if (!EmailFormat.looksLikeEmail(fromEmail)) {
            log.warn("[EMAIL:SMTP] disabled: invalid sender email (EMAIL_SMTP_FROM_EMAIL or smtpFromEmail in admin settings).");
            return null;
        }
        if (authEnabled && (username == null || username.isBlank() || password == null || password.isBlank())) {
            log.warn("[EMAIL:SMTP] disabled: SMTP auth enabled but username/password are not configured.");
            return null;
        }
        if (fallbackTo != null && !EmailFormat.looksLikeEmail(fallbackTo)) {
            fallbackTo = null;
        }

        return new EffectiveConfig(host, port, username, password, fromEmail, senderName,
                authEnabled, starttlsEnabled, sslEnabled, fallbackTo);
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
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private boolean parseBooleanOrDefault(String rawValue, boolean defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(rawValue.trim());
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

    record EffectiveConfig(
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
                message.subject(), message.bodyText(), message.bodyHtml());
    }
}
