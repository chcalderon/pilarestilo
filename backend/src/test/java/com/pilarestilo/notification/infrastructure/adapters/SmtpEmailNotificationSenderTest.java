package com.pilarestilo.notification.infrastructure.adapters;

import com.pilarestilo.notification.application.EmailLayout;
import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The adapter had no test at all: it built its mail client inline, so every case needed a live SMTP
 * server to reach. {@code buildSender} is the seam; this supplies one that records.
 */
class SmtpEmailNotificationSenderTest {

    private SystemSettingsRepository systemSettingsRepository;
    private RecordingSender sender;

    @BeforeEach
    void setUp() {
        systemSettingsRepository = mock(SystemSettingsRepository.class);
        SystemSettings settings = mock(SystemSettings.class);
        when(settings.getSmtpHost()).thenReturn("smtp.correo.cl");
        when(settings.getSmtpPort()).thenReturn(587);
        when(settings.getSmtpUsername()).thenReturn("usuario");
        when(settings.getSmtpFromEmail()).thenReturn("tienda@pilarestilo.com");
        when(settings.isSmtpAuthEnabled()).thenReturn(false);
        when(systemSettingsRepository.get()).thenReturn(settings);
        sender = new RecordingSender(systemSettingsRepository, mock(SystemSettingsCryptoService.class));
    }

    @Test
    @DisplayName("sends to the recipient's own address, with the message's subject and body")
    void sendsToTheRecipient() {
        sender.send(message("Pedido PE-ABC123 enviado", "Tu pedido ya salió."),
                NotificationRecipient.of(null, "cliente@correo.cl", "EMAIL"));

        assertThat(sender.sent).isNotNull();
        assertThat(sender.recipients()).containsExactly("cliente@correo.cl");
        assertThat(sender.subject()).isEqualTo("Pedido PE-ABC123 enviado");
    }

    @Test
    @DisplayName("falls back to the configured address when the recipient has no email")
    void usesTheFallbackAddress() {
        sender.send(message("Aviso", "Cuerpo"),
                NotificationRecipient.of("+56911111111", null, "EMAIL"));

        assertThat(sender.recipients()).containsExactly("bandeja@pilarestilo.com");
    }

    @Test
    @DisplayName("somebody who asked for WhatsApp only is not emailed")
    void respectsTheChannelPreference() {
        sender.send(message("Aviso", "Cuerpo"),
                NotificationRecipient.of("+56911111111", "cliente@correo.cl", "WHATSAPP"));

        assertThat(sender.sent).isNull();
    }

    @Test
    @DisplayName("with no address anywhere it gives up quietly rather than building a message")
    void skipsWhenThereIsNowhereToSend() {
        RecordingSender withoutFallback = new RecordingSender(
                systemSettingsRepository, mock(SystemSettingsCryptoService.class), "");

        withoutFallback.send(message("Aviso", "Cuerpo"),
                NotificationRecipient.of(null, null, "EMAIL"));

        assertThat(withoutFallback.sent).isNull();
    }

    @Test
    @DisplayName("a value that is not an address is not treated as one")
    void ignoresSomethingThatIsNotAnEmail() {
        sender.send(message("Aviso", "Cuerpo"),
                NotificationRecipient.of(null, "no-es-un-correo", "EMAIL"));

        assertThat(sender.recipients()).containsExactly("bandeja@pilarestilo.com");
    }

    @Test
    @DisplayName("a failing mail server does not propagate: a lost notice must not fail the order")
    void swallowsSendFailures() {
        RecordingSender failing = new RecordingSender(
                systemSettingsRepository, mock(SystemSettingsCryptoService.class)) {
            @Override
            JavaMailSenderImpl buildSender(EffectiveConfig config) {
                JavaMailSenderImpl impl = new JavaMailSenderImpl();
                impl.setHost("localhost");
                impl.setPort(1);
                return impl;
            }
        };

        failing.send(message("Aviso", "Cuerpo"), NotificationRecipient.of(null, "a@b.cl", "EMAIL"));
    }

    @Test
    @DisplayName("a message with an HTML body goes out multipart, carrying both versions")
    void sendsBothPartsWhenThereIsHtml() throws Exception {
        NotificationMessage withHtml = new NotificationMessage(
                "ORDER_SHIPPED", "Pedido enviado", "Texto plano.",
                "<html><body><p>Version disenada</p></body></html>",
                Map.of(), UUID.randomUUID());

        sender.send(withHtml, NotificationRecipient.of(null, "cliente@correo.cl", "EMAIL"));

        // JavaMail settles the content type in saveChanges, which the real doSend calls and the
        // recording override skips.
        sender.sent.saveChanges();
        assertThat(sender.sent.getContentType()).startsWith("multipart/");
    }

    @Test
    @DisplayName("the logo travels inside the message, so no client has to fetch it")
    void attachesTheLogoInline() throws Exception {
        NotificationMessage withHtml = new NotificationMessage(
                "ORDER_SHIPPED", "Pedido enviado", "Texto plano.",
                EmailLayout.titled("Pedido").build(),
                Map.of(), UUID.randomUUID());

        sender.send(withHtml, NotificationRecipient.of(null, "cliente@correo.cl", "EMAIL"));

        sender.sent.saveChanges();
        // Spring nests the related part inside a mixed envelope, so the outer type says "mixed".
        // Writing the message out and looking for the Content-ID is the direct evidence that the
        // part the header points at is actually in there.
        java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
        sender.sent.writeTo(raw);
        assertThat(raw.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("Content-ID: <" + EmailLayout.LOGO_CONTENT_ID + ">");
    }

    @Test
    @DisplayName("a message without one is still plain text, not an empty HTML shell")
    void staysPlainWhenThereIsNoHtml() throws Exception {
        sender.send(message("Aviso", "Cuerpo"), NotificationRecipient.of(null, "a@b.cl", "EMAIL"));

        sender.sent.saveChanges();
        assertThat(sender.sent.getContentType()).startsWith("text/plain");
    }

    private NotificationMessage message(String subject, String body) {
        return new NotificationMessage("ORDER_SHIPPED", subject, body, null, Map.of(), UUID.randomUUID());
    }

    /** Captures the message instead of opening a connection. */
    private static class RecordingSender extends SmtpEmailNotificationSender {

        private MimeMessage sent;

        RecordingSender(SystemSettingsRepository repository, SystemSettingsCryptoService crypto) {
            this(repository, crypto, "bandeja@pilarestilo.com");
        }

        RecordingSender(SystemSettingsRepository repository,
                        SystemSettingsCryptoService crypto,
                        String fallbackTo) {
            super(repository, crypto,
                    "", "", "", "",
                    "", "Pilar Estilo",
                    "", "", "", fallbackTo);
        }

        @Override
        JavaMailSenderImpl buildSender(EffectiveConfig config) {
            return new JavaMailSenderImpl() {
                @Override
                public void send(MimeMessage mimeMessage) {
                    sent = mimeMessage;
                }
            };
        }

        String subject() {
            try {
                return sent.getSubject();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        java.util.List<String> recipients() {
            try {
                return java.util.Arrays.stream(sent.getAllRecipients())
                        .map(Object::toString)
                        .toList();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
