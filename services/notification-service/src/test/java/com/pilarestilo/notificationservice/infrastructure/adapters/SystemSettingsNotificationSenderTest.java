package com.pilarestilo.notificationservice.infrastructure.adapters;

import com.pilarestilo.notificationservice.domain.model.NotificationMessage;
import com.pilarestilo.notificationservice.domain.model.NotificationRecipient;
import com.pilarestilo.notificationservice.domain.ports.MessagingSettingsPort;
import com.pilarestilo.notificationservice.domain.view.MessagingSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemSettingsNotificationSenderTest {

    private final MessagingSettingsPort settingsPort = mock(MessagingSettingsPort.class);
    private final LogNotificationSender logSender = mock(LogNotificationSender.class);
    private final SimulatedWhatsAppNotificationSender simulated = mock(SimulatedWhatsAppNotificationSender.class);
    private final TwilioWhatsAppNotificationSender twilio = mock(TwilioWhatsAppNotificationSender.class);
    private final SendGridEmailNotificationSender sendgrid = mock(SendGridEmailNotificationSender.class);
    private final SmtpEmailNotificationSender smtp = mock(SmtpEmailNotificationSender.class);
    private final N8nWebhookNotificationSender n8n = mock(N8nWebhookNotificationSender.class);

    private SystemSettingsNotificationSender sender(String envDefault) {
        return new SystemSettingsNotificationSender(settingsPort, logSender, simulated, twilio,
                sendgrid, smtp, n8n, envDefault);
    }

    private MessagingSettings settingsWith(List<String> providers, String updatedBy) {
        return new MessagingSettings(providers, updatedBy,
                null, null, null, null, null, false, false,
                null, null, null, null, null,
                null, null, null, null, null, null,
                null, null,
                null, null, null,
                false, 0);
    }

    private NotificationMessage message() {
        return new NotificationMessage("WELCOME", "Bienvenida", "Hola", null, Map.of(), UUID.randomUUID());
    }

    @Test
    void fans_out_to_every_configured_provider() {
        when(settingsPort.current()).thenReturn(settingsWith(List.of("EMAIL_SMTP", "WHATSAPP_TWILIO"), "admin@x"));

        sender("LOG").send(message(), NotificationRecipient.unknown());

        verify(smtp).send(any(), any());
        verify(twilio).send(any(), any());
        verify(logSender, never()).send(any(), any());
    }

    @Test
    void one_provider_throwing_does_not_stop_the_others() {
        when(settingsPort.current()).thenReturn(settingsWith(List.of("EMAIL_SMTP", "WHATSAPP_TWILIO"), "admin@x"));
        doThrow(new RuntimeException("smtp down")).when(smtp).send(any(), any());

        sender("LOG").send(message(), NotificationRecipient.unknown());

        verify(twilio).send(any(), any());
    }

    @Test
    void a_system_seeded_LOG_row_defers_to_the_env_fallback() {
        when(settingsPort.current()).thenReturn(settingsWith(List.of("LOG"), "system-seed"));

        sender("EMAIL_SMTP").send(message(), NotificationRecipient.unknown());

        verify(smtp).send(any(), any());
        verify(logSender, never()).send(any(), any());
    }

    @Test
    void a_LOG_row_saved_by_an_admin_is_respected() {
        when(settingsPort.current()).thenReturn(settingsWith(List.of("LOG"), "admin@pilarestilo.com"));

        sender("EMAIL_SMTP").send(message(), NotificationRecipient.unknown());

        verify(logSender).send(any(), any());
        verify(smtp, never()).send(any(), any());
    }

    @Test
    void an_empty_list_falls_back_to_the_env_default() {
        when(settingsPort.current()).thenReturn(settingsWith(List.of(), null));

        sender("N8N_WEBHOOK").send(message(), NotificationRecipient.unknown());

        verify(n8n).send(any(), any());
    }
}
