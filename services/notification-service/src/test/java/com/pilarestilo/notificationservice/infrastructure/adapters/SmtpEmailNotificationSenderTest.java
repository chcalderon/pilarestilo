package com.pilarestilo.notificationservice.infrastructure.adapters;

import com.pilarestilo.notificationservice.domain.model.NotificationMessage;
import com.pilarestilo.notificationservice.domain.model.NotificationRecipient;
import com.pilarestilo.notificationservice.domain.ports.MessagingSettingsPort;
import com.pilarestilo.notificationservice.domain.view.MessagingSettings;
import com.pilarestilo.notificationservice.metrics.NotificationMetrics;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpEmailNotificationSenderTest {

    private final MessagingSettingsPort settingsPort = mock(MessagingSettingsPort.class);
    private final SystemSettingsCryptoService crypto = new SystemSettingsCryptoService("key-key-key-key");
    private final NotificationMetrics metrics = mock(NotificationMetrics.class);

    private final AtomicInteger sent = new AtomicInteger();

    private SmtpEmailNotificationSender sender() {
        return new SmtpEmailNotificationSender(settingsPort, crypto, metrics,
                "", "", "", "", "", "Pilar Estilo", "", "", "", "") {
            @Override
            JavaMailSenderImpl buildSender(EffectiveConfig config) {
                return new JavaMailSenderImpl() {
                    @Override
                    public void send(MimeMessage mimeMessage) {
                        sent.incrementAndGet();
                    }
                };
            }
        };
    }

    private MessagingSettings configured() {
        return new MessagingSettings(
                List.of(), null,
                "smtp.example.com", 587, "user", "envios@pilarestilo.com", null, false, true,
                null, null, null, null, null,
                null, null, null, null, null, null,
                null, null,
                null, null, null,
                false, 0);
    }

    @BeforeEach
    void setUp() {
        sent.set(0);
    }

    @Test
    void sends_when_smtp_is_configured() {
        when(settingsPort.current()).thenReturn(configured());

        sender().send(message(), NotificationRecipient.of(null, "cliente@example.com", "EMAIL"));

        assertThat(sent).hasValue(1);
    }

    @Test
    void skips_when_the_recipient_opted_out_of_email() {
        sender().send(message(), NotificationRecipient.of("+56911111111", "cliente@example.com", "WHATSAPP"));

        assertThat(sent).hasValue(0);
    }

    @Test
    void is_disabled_when_no_host_is_configured() {
        when(settingsPort.current()).thenReturn(MessagingSettings.empty());

        sender().send(message(), NotificationRecipient.of(null, "cliente@example.com", "EMAIL"));

        assertThat(sent).hasValue(0);
    }

    @Test
    void counts_a_failure_when_the_transport_throws() {
        when(settingsPort.current()).thenReturn(configured());
        SmtpEmailNotificationSender failing = new SmtpEmailNotificationSender(settingsPort, crypto, metrics,
                "", "", "", "", "", "Pilar Estilo", "", "", "", "") {
            @Override
            JavaMailSenderImpl buildSender(EffectiveConfig config) {
                return new JavaMailSenderImpl() {
                    @Override
                    public void send(MimeMessage mimeMessage) {
                        throw new org.springframework.mail.MailSendException("smtp down");
                    }
                };
            }
        };

        failing.send(message(), NotificationRecipient.of(null, "cliente@example.com", "EMAIL"));

        verify(metrics).countSendFailure("EMAIL_SMTP");
    }

    private NotificationMessage message() {
        return new NotificationMessage("ORDER_CONFIRMATION", "Pedido confirmado",
                "Tu pedido fue creado.", null, Map.of(), UUID.randomUUID());
    }
}
