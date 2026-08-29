package com.pilarestilo.shared.auth.infrastructure.email;

import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmtpPasswordResetMailerTest {

    private SystemSettingsRepository systemSettingsRepository;

    @BeforeEach
    void setUp() {
        systemSettingsRepository = mock(SystemSettingsRepository.class);
    }

    private void smtpConfigured() {
        SystemSettings settings = mock(SystemSettings.class);
        when(settings.getSmtpHost()).thenReturn("smtp.correo.cl");
        when(settings.getSmtpPort()).thenReturn(587);
        when(settings.getSmtpFromEmail()).thenReturn("tienda@pilarestilo.com");
        when(settings.isSmtpAuthEnabled()).thenReturn(false);
        when(systemSettingsRepository.get()).thenReturn(settings);
    }

    private void smtpNotConfigured() {
        SystemSettings settings = mock(SystemSettings.class);
        when(systemSettingsRepository.get()).thenReturn(settings);
    }

    @Test
    void sends_a_message_carrying_the_reset_link_when_smtp_is_configured() throws Exception {
        smtpConfigured();
        RecordingMailer mailer = new RecordingMailer(systemSettingsRepository, "https://pilarestilo.cl");

        mailer.sendResetLink("cliente@example.com", "Camila", "TOK-123");

        assertThat(mailer.sent).isNotNull();
        assertThat(mailer.sent.getSubject()).isEqualTo("Restablece tu contraseña — Pilar Estilo");
        String body = textOf(mailer.sent);
        assertThat(body).contains("https://pilarestilo.cl/es/reset-password?token=TOK-123");
        assertThat(body).contains("30 minutos");
    }

    @Test
    void a_multi_host_base_url_collapses_to_the_first_host() throws Exception {
        smtpConfigured();
        RecordingMailer mailer = new RecordingMailer(
                systemSettingsRepository, "https://pilarestilo.com www.pilarestilo.com");

        mailer.sendResetLink("cliente@example.com", "Camila", "TOK-123");

        String body = textOf(mailer.sent);
        assertThat(body).contains("https://pilarestilo.com/es/reset-password?token=TOK-123");
        assertThat(body).doesNotContain("www.pilarestilo.com");
    }

    @Test
    void is_a_no_op_when_smtp_is_not_configured() {
        smtpNotConfigured();
        RecordingMailer mailer = new RecordingMailer(systemSettingsRepository, "https://pilarestilo.cl");

        assertThatCode(() -> mailer.sendResetLink("cliente@example.com", "Camila", "TOK-123"))
                .doesNotThrowAnyException();
        assertThat(mailer.sent).isNull();
    }

    @Test
    void is_a_no_op_when_the_recipient_address_is_not_an_email() {
        smtpConfigured();
        RecordingMailer mailer = new RecordingMailer(systemSettingsRepository, "https://pilarestilo.cl");

        mailer.sendResetLink("not-an-email", "Camila", "TOK-123");

        assertThat(mailer.sent).isNull();
    }

    @Test
    void a_failing_mail_server_does_not_propagate() {
        smtpConfigured();
        RecordingMailer failing = new RecordingMailer(systemSettingsRepository, "https://pilarestilo.cl") {
            @Override
            JavaMailSenderImpl buildSender(SmtpConfig config) {
                JavaMailSenderImpl impl = new JavaMailSenderImpl();
                impl.setHost("localhost");
                impl.setPort(1);
                return impl;
            }
        };

        assertThatCode(() -> failing.sendResetLink("a@b.cl", "A", "TOK-123")).doesNotThrowAnyException();
    }

    /** Walks the MIME tree and concatenates every text/* part, so quoted-printable is decoded for us. */
    private static String textOf(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                out.append(textOf(multipart.getBodyPart(i)));
            }
            return out.toString();
        }
        return "";
    }

    /** Captures the message instead of opening a connection. */
    private static class RecordingMailer extends SmtpPasswordResetMailer {

        private MimeMessage sent;

        RecordingMailer(SystemSettingsRepository repository, String linkBaseUrl) {
            super(repository, mock(SystemSettingsCryptoService.class),
                    linkBaseUrl, 30,
                    "", "", "", "", "", "Pilar Estilo", "", "", "");
        }

        @Override
        JavaMailSenderImpl buildSender(SmtpConfig config) {
            return new JavaMailSenderImpl() {
                @Override
                public void send(MimeMessage mimeMessage) {
                    sent = mimeMessage;
                }
            };
        }
    }
}
