package com.pilarestilo.shared.auth.infrastructure.email;

import com.pilarestilo.shared.auth.domain.ports.PasswordResetMailer;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Sends the password-reset code over SMTP, self-contained on purpose.
 *
 * <p>Account recovery must work when the shop's notification pipeline is down and regardless of
 * which channels an admin has toggled on, so this does not go through {@code notification-service},
 * Kafka, or {@code system_settings.notification_providers}. It reads the same SMTP credentials
 * everything else does — {@code system_settings} first, then {@code EMAIL_SMTP_*} — and sends
 * directly. A misconfigured or dead server is logged and swallowed; the use case treats a mailer
 * failure as non-fatal so a broken SMTP host cannot leak "this address exists".
 *
 * <p>The email carries a 6-digit code and the route to type it in — never a link.
 */
@Component
public class SmtpPasswordResetMailer implements PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetMailer.class);
    private static final String SUBJECT = "Restablece tu contraseña — Pilar Estilo";

    private final SystemSettingsRepository systemSettingsRepository;
    private final SystemSettingsCryptoService cryptoService;
    private final int codeTtlMinutes;
    private final String envHost;
    private final String envPort;
    private final String envUsername;
    private final String envPassword;
    private final String envFromEmail;
    private final String senderName;
    private final String envAuthEnabled;
    private final String envStarttlsEnabled;
    private final String envSslEnabled;

    public SmtpPasswordResetMailer(
            SystemSettingsRepository systemSettingsRepository,
            SystemSettingsCryptoService cryptoService,
            @Value("${app.password-reset.code-ttl-minutes:30}") int codeTtlMinutes,
            @Value("${EMAIL_SMTP_HOST:}") String envHost,
            @Value("${EMAIL_SMTP_PORT:}") String envPort,
            @Value("${EMAIL_SMTP_USERNAME:}") String envUsername,
            @Value("${EMAIL_SMTP_PASSWORD:}") String envPassword,
            @Value("${EMAIL_SMTP_FROM_EMAIL:}") String envFromEmail,
            @Value("${EMAIL_SMTP_SENDER_NAME:Pilar Estilo}") String senderName,
            @Value("${EMAIL_SMTP_AUTH_ENABLED:}") String envAuthEnabled,
            @Value("${EMAIL_SMTP_STARTTLS_ENABLED:}") String envStarttlsEnabled,
            @Value("${EMAIL_SMTP_SSL_ENABLED:}") String envSslEnabled
    ) {
        this.systemSettingsRepository = systemSettingsRepository;
        this.cryptoService = cryptoService;
        this.codeTtlMinutes = codeTtlMinutes;
        this.envHost = trimToEmpty(envHost);
        this.envPort = trimToEmpty(envPort);
        this.envUsername = trimToEmpty(envUsername);
        this.envPassword = trimToEmpty(envPassword);
        this.envFromEmail = trimToEmpty(envFromEmail);
        this.senderName = blankToDefault(senderName, "Pilar Estilo");
        this.envAuthEnabled = trimToEmpty(envAuthEnabled);
        this.envStarttlsEnabled = trimToEmpty(envStarttlsEnabled);
        this.envSslEnabled = trimToEmpty(envSslEnabled);
    }

    @Override
    public void sendResetCode(String toEmail, String fullName, String code) {
        if (!looksLikeEmail(toEmail)) {
            log.warn("[EMAIL:RESET] skipped: recipient address is not valid");
            return;
        }
        SmtpConfig config = resolveConfig();
        if (config == null) {
            return;
        }

        String greeting = (fullName == null || fullName.isBlank()) ? "Hola" : "Hola " + fullName.trim();
        String text = greeting + ".\n\n"
                + "Recibimos una solicitud para cambiar la contraseña de tu cuenta en Pilar Estilo.\n\n"
                + "Tu código: " + code + "\n\n"
                + "Entra a pilarestilo.com, abre \"¿Olvidaste tu contraseña?\", escribe tu correo y "
                + "el código, y elige una nueva contraseña.\n\n"
                + "El código vence en " + codeTtlMinutes + " minutos y se usa una sola vez. "
                + "Si no fuiste tú, ignora este correo: tu contraseña actual sigue válida.\n";
        String html = AuthEmailLayout.titled("Código para cambiar tu contraseña")
                .eyebrow("Seguridad")
                .paragraph(greeting + ". Recibimos una solicitud para cambiar la contraseña de tu "
                        + "cuenta. Si fuiste tú, usa este código:")
                .code(code, null)
                .route("Cómo usarlo", "Entra a", "pilarestilo.com",
                        "Iniciar sesión › ¿Olvidaste tu contraseña?")
                .paragraph("Escribe tu correo y el código, y elige tu nueva contraseña.")
                .note("Importante", "El código vence en " + codeTtlMinutes + " minutos y se usa una "
                        + "sola vez. Si no fuiste tú, ignora este correo: tu contraseña actual sigue válida.")
                .build();

        JavaMailSenderImpl sender = buildSender(config);
        String to = toEmail.trim();
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(config.fromEmail(), senderName);
            helper.setTo(to);
            helper.setSubject(SUBJECT);
            helper.setText(text, html);
            helper.addInline(AuthEmailLayout.LOGO_CONTENT_ID, new ClassPathResource(AuthEmailLayout.LOGO_RESOURCE));
            sender.send(message);
            log.info("[EMAIL:RESET] sent to={}", to);
        } catch (Exception ex) {
            log.warn("[EMAIL:RESET] send failed to={} reason={}", to, ex.getMessage());
        }
    }

    /** A seam for the test beside this class: it supplies a sender that records instead of connecting. */
    JavaMailSenderImpl buildSender(SmtpConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host());
        sender.setPort(config.port());
        if (!config.username().isBlank()) {
            sender.setUsername(config.username());
        }
        if (!config.password().isBlank()) {
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

    private SmtpConfig resolveConfig() {
        var settings = systemSettingsRepository.get();
        String host = firstNonBlank(settings.getSmtpHost(), envHost);
        Integer port = parsePort(firstNonBlank(
                settings.getSmtpPort() == null ? null : String.valueOf(settings.getSmtpPort()), envPort));
        String username = trimToEmpty(firstNonBlank(settings.getSmtpUsername(), envUsername));
        String password = trimToEmpty(firstNonBlank(decrypt(settings.getSmtpPasswordEncrypted()), envPassword));
        String fromEmail = firstNonBlank(settings.getSmtpFromEmail(), envFromEmail);
        boolean authEnabled = parseBooleanOrDefault(envAuthEnabled, settings.isSmtpAuthEnabled());
        boolean starttlsEnabled = parseBooleanOrDefault(envStarttlsEnabled, settings.isSmtpStarttlsEnabled());
        boolean sslEnabled = parseBooleanOrDefault(envSslEnabled, port != null && port == 465);

        if (host == null || host.isBlank()) {
            log.warn("[EMAIL:RESET] disabled: no SMTP host (EMAIL_SMTP_HOST or smtpHost in admin settings)");
            return null;
        }
        if (port == null || port < 1 || port > 65535) {
            log.warn("[EMAIL:RESET] disabled: missing/invalid SMTP port");
            return null;
        }
        if (!looksLikeEmail(fromEmail)) {
            log.warn("[EMAIL:RESET] disabled: invalid sender email (EMAIL_SMTP_FROM_EMAIL or smtpFromEmail)");
            return null;
        }
        if (authEnabled && (username.isBlank() || password.isBlank())) {
            log.warn("[EMAIL:RESET] disabled: SMTP auth is on but username/password are not set");
            return null;
        }
        return new SmtpConfig(host.trim(), port, username, password, fromEmail.trim(),
                authEnabled, starttlsEnabled, sslEnabled);
    }

    private String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        try {
            String decrypted = cryptoService.decrypt(encrypted);
            return (decrypted == null || decrypted.isBlank()) ? null : decrypted.trim();
        } catch (DomainException ex) {
            log.warn("[EMAIL:RESET] could not decrypt the stored SMTP password: {}", ex.getMessage());
            return null;
        }
    }

    private static Integer parsePort(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static boolean parseBooleanOrDefault(String rawValue, boolean defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(rawValue.trim());
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private static boolean looksLikeEmail(String value) {
        // Possessive quantifiers (and a dot-free local/domain-label class) keep this linear — the
        // plain "[^@\s]+@[^@\s]+\.[^@\s]+" form backtracks polynomially on a near-miss (Sonar S8786).
        return value != null && value.matches("[^@\\s]++@[^@\\s.]++\\.[^@\\s]++");
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    record SmtpConfig(String host, int port, String username, String password, String fromEmail,
                      boolean authEnabled, boolean starttlsEnabled, boolean sslEnabled) {}
}
