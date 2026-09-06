package com.pilarestilo.shared.auth.infrastructure.email;

import com.pilarestilo.shared.auth.domain.ports.PasswordResetMailer;
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
import java.util.Properties;

/**
 * Sends the password-reset link over SMTP, self-contained on purpose.
 *
 * <p>Account recovery must work when the shop's notification pipeline is down and regardless of
 * which channels an admin has toggled on, so this does not go through {@code notification-service},
 * Kafka, or {@code system_settings.notification_providers}. It reads the same SMTP credentials
 * everything else does — {@code system_settings} first, then {@code EMAIL_SMTP_*} — and sends
 * directly. A misconfigured or dead server is logged and swallowed; the use case treats a mailer
 * failure as non-fatal so a broken SMTP host cannot leak "this address exists".
 */
@Component
public class SmtpPasswordResetMailer implements PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetMailer.class);
    private static final String SUBJECT = "Restablece tu contraseña — Pilar Estilo";

    private final SystemSettingsRepository systemSettingsRepository;
    private final SystemSettingsCryptoService cryptoService;
    private final String linkBaseUrl;
    private final int tokenTtlMinutes;
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
            @Value("${app.password-reset.link-base-url:http://localhost:4321}") String linkBaseUrl,
            @Value("${app.password-reset.token-ttl-minutes:30}") int tokenTtlMinutes,
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
        this.linkBaseUrl = stripTrailingSlash(firstToken(blankToDefault(linkBaseUrl, "http://localhost:4321")));
        this.tokenTtlMinutes = tokenTtlMinutes;
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
    public void sendResetLink(String toEmail, String fullName, String rawToken) {
        if (!looksLikeEmail(toEmail)) {
            log.warn("[EMAIL:RESET] skipped: recipient address is not valid");
            return;
        }
        SmtpConfig config = resolveConfig();
        if (config == null) {
            return;
        }

        String link = linkBaseUrl + "/es/auth/reset-password?token=" + rawToken;
        String greetingName = (fullName == null || fullName.isBlank()) ? "" : " " + fullName.trim();
        String text = """
                Hola%s,

                Recibimos una solicitud para restablecer la contraseña de tu cuenta en Pilar Estilo.
                Abre este enlace para elegir una nueva contraseña:

                %s

                El enlace expira en %d minutos y solo puede usarse una vez.
                Si no fuiste tú, ignora este correo: tu contraseña actual sigue siendo válida.

                Pilar Estilo
                """.formatted(greetingName, link, tokenTtlMinutes);
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;font-size:15px;color:#1f2937;line-height:1.5">
                  <p>Hola%s,</p>
                  <p>Recibimos una solicitud para restablecer la contraseña de tu cuenta en Pilar Estilo.</p>
                  <p><a href="%s" style="display:inline-block;padding:10px 18px;background:#b8446b;color:#ffffff;text-decoration:none;border-radius:6px">Elegir una nueva contraseña</a></p>
                  <p style="font-size:13px;color:#6b7280">O copia este enlace: <br>%s</p>
                  <p style="font-size:13px;color:#6b7280">El enlace expira en %d minutos y solo puede usarse una vez. Si no fuiste tú, ignora este correo: tu contraseña actual sigue siendo válida.</p>
                  <p>Pilar Estilo</p>
                </div>
                """.formatted(escapeHtml(greetingName), escapeHtml(link), escapeHtml(link), tokenTtlMinutes);

        JavaMailSenderImpl sender = buildSender(config);
        String to = toEmail.trim();
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(config.fromEmail(), senderName);
            helper.setTo(to);
            helper.setSubject(SUBJECT);
            helper.setText(text, html);
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

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * The storefront origin, first token only. {@code DOMAIN} in the shipped env may hold several
     * space-separated hostnames (so Caddy serves them all); {@code https://${DOMAIN}} would then be
     * two hosts joined by a space, which URL-encodes to a broken link.
     */
    private static String firstToken(String value) {
        String trimmed = value.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    record SmtpConfig(String host, int port, String username, String password, String fromEmail,
                      boolean authEnabled, boolean starttlsEnabled, boolean sslEnabled) {}
}
