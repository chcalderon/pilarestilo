package com.pilarestilo.notification.infrastructure.adapters;

import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.systemsettings.domain.enums.NotificationProvider;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The direct regression test for the silent no-op.
 *
 * <p>{@code sendOrderCancelled} was a {@code default} method with an empty body on the port. Only
 * LogNotificationSender implemented it, so under EMAIL_SMTP, SendGrid, WhatsApp or n8n a customer
 * whose order was auto-cancelled was simply never told — no error, no log, nothing. Replacing it
 * with an abstract {@code send} makes that impossible to reintroduce, and this asserts every
 * provider actually forwards.
 */
@ExtendWith(MockitoExtension.class)
class SystemSettingsNotificationSenderRoutingTest {

    @Mock SystemSettingsRepository systemSettingsRepository;
    @Mock SystemSettings settings;

    private final LogNotificationSender log = mock(LogNotificationSender.class);
    private final SimulatedWhatsAppNotificationSender simulated = mock(SimulatedWhatsAppNotificationSender.class);
    private final TwilioWhatsAppNotificationSender twilio = mock(TwilioWhatsAppNotificationSender.class);
    private final SendGridEmailNotificationSender sendgrid = mock(SendGridEmailNotificationSender.class);
    private final SmtpEmailNotificationSender smtp = mock(SmtpEmailNotificationSender.class);
    private final N8nWebhookNotificationSender n8n = mock(N8nWebhookNotificationSender.class);

    private SystemSettingsNotificationSender routerFor(NotificationProvider provider) {
        lenient().when(settings.getNotificationProvider()).thenReturn(provider);
        lenient().when(systemSettingsRepository.get()).thenReturn(settings);
        return new SystemSettingsNotificationSender(
                systemSettingsRepository, log, simulated, twilio, sendgrid, smtp, n8n,
                "LOG");
    }

    private NotificationMessage message() {
        return new NotificationMessage(
                NotificationMessage.ORDER_CANCELLED, "asunto", "cuerpo", null,
                Map.of("orderReference", "PE-3F9A2C71B4"), UUID.randomUUID());
    }


    /**
     * Parameterised over the whole enum on purpose: a provider added later is covered the moment it
     * exists, rather than being forgotten the way sendOrderCancelled was.
     */
    @ParameterizedTest
    @EnumSource(NotificationProvider.class)
    void everyProviderForwardsTheMessage(NotificationProvider provider) {
        var router = routerFor(provider);
        var recipient = NotificationRecipient.of("+56900000000", "cliente@test.com", "BOTH");
        var message = message();

        router.send(message, recipient);

        switch (provider) {
            case LOG -> verify(log).send(eq(message), any());
            case WHATSAPP_SIMULATED -> verify(simulated).send(eq(message), any());
            case WHATSAPP_TWILIO -> verify(twilio).send(eq(message), any());
            case EMAIL_SENDGRID -> verify(sendgrid).send(eq(message), any());
            case EMAIL_SMTP -> verify(smtp).send(eq(message), any());
            case N8N_WEBHOOK -> verify(n8n).send(eq(message), any());
        }
    }

    @Test
    void fallsBackToTheEnvironmentProviderWhenSettingsHaveNone() {
        lenient().when(settings.getNotificationProvider()).thenReturn(null);
        lenient().when(systemSettingsRepository.get()).thenReturn(settings);
        var router = new SystemSettingsNotificationSender(
                systemSettingsRepository, log, simulated, twilio, sendgrid, smtp, n8n,
                "EMAIL_SMTP");

        router.send(message(), NotificationRecipient.unknown());

        verify(smtp).send(any(), any());
    }
}
