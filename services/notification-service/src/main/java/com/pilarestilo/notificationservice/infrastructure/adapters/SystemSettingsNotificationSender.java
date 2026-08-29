package com.pilarestilo.notificationservice.infrastructure.adapters;

import com.pilarestilo.notificationservice.domain.enums.NotificationProvider;
import com.pilarestilo.notificationservice.domain.model.NotificationMessage;
import com.pilarestilo.notificationservice.domain.model.NotificationRecipient;
import com.pilarestilo.notificationservice.domain.ports.MessagingSettingsPort;
import com.pilarestilo.notificationservice.domain.ports.NotificationSender;
import com.pilarestilo.notificationservice.domain.view.MessagingSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Sends over every channel the shop has enabled, not one of them. Each channel is attempted on its
 * own — a dead SMTP host cannot swallow WhatsApp — and if none accepts, that is an error, not
 * silence. Ported from the monolith; the provider list now comes from {@link MessagingSettingsPort}.
 */
@Component
@Primary
public class SystemSettingsNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingsNotificationSender.class);

    private final MessagingSettingsPort messagingSettings;
    private final LogNotificationSender logNotificationSender;
    private final SimulatedWhatsAppNotificationSender simulatedWhatsAppNotificationSender;
    private final TwilioWhatsAppNotificationSender twilioWhatsAppNotificationSender;
    private final SendGridEmailNotificationSender sendGridEmailNotificationSender;
    private final SmtpEmailNotificationSender smtpEmailNotificationSender;
    private final N8nWebhookNotificationSender n8nWebhookNotificationSender;
    private final NotificationProvider envDefaultProvider;

    public SystemSettingsNotificationSender(
            MessagingSettingsPort messagingSettings,
            LogNotificationSender logNotificationSender,
            SimulatedWhatsAppNotificationSender simulatedWhatsAppNotificationSender,
            TwilioWhatsAppNotificationSender twilioWhatsAppNotificationSender,
            SendGridEmailNotificationSender sendGridEmailNotificationSender,
            SmtpEmailNotificationSender smtpEmailNotificationSender,
            N8nWebhookNotificationSender n8nWebhookNotificationSender,
            @Value("${app.notification.provider:LOG}") String envDefaultProvider
    ) {
        this.messagingSettings = messagingSettings;
        this.logNotificationSender = logNotificationSender;
        this.simulatedWhatsAppNotificationSender = simulatedWhatsAppNotificationSender;
        this.twilioWhatsAppNotificationSender = twilioWhatsAppNotificationSender;
        this.sendGridEmailNotificationSender = sendGridEmailNotificationSender;
        this.smtpEmailNotificationSender = smtpEmailNotificationSender;
        this.n8nWebhookNotificationSender = n8nWebhookNotificationSender;
        this.envDefaultProvider = parseProvider(envDefaultProvider, NotificationProvider.LOG);
    }

    @Override
    public void send(NotificationMessage message, NotificationRecipient recipient) {
        Set<NotificationProvider> providers = resolveProviders();
        boolean delivered = false;
        for (NotificationProvider provider : providers) {
            try {
                senderFor(provider).send(message, recipient);
                delivered = true;
            } catch (RuntimeException ex) {
                log.warn("Notification provider {} failed for template {}: {}",
                        provider, message.templateKey(), ex.getMessage());
            }
        }
        if (!delivered) {
            log.error("No notification channel accepted template {} ({} configured)",
                    message.templateKey(), providers);
        }
    }

    private NotificationSender senderFor(NotificationProvider provider) {
        return switch (provider) {
            case WHATSAPP_SIMULATED -> simulatedWhatsAppNotificationSender;
            case WHATSAPP_TWILIO -> twilioWhatsAppNotificationSender;
            case EMAIL_SENDGRID -> sendGridEmailNotificationSender;
            case EMAIL_SMTP -> smtpEmailNotificationSender;
            case N8N_WEBHOOK -> n8nWebhookNotificationSender;
            case LOG -> logNotificationSender;
        };
    }

    private Set<NotificationProvider> resolveProviders() {
        try {
            MessagingSettings settings = messagingSettings.current();
            Set<NotificationProvider> configured = new LinkedHashSet<>();
            for (String raw : settings.notificationProviders()) {
                configured.add(NotificationProvider.fromRaw(raw));
            }
            if (!configured.isEmpty()) {
                // A seeded row still on LOG is not a decision anybody made, so the environment
                // gets to speak. Once an admin saves the panel the stored set wins, LOG included.
                if (configured.equals(Set.of(NotificationProvider.LOG)) && settings.seededBySystem()) {
                    return Set.of(envDefaultProvider);
                }
                return configured;
            }
        } catch (Exception ex) {
            log.warn("Could not read notification providers from settings, using env fallback: {}",
                    ex.getMessage());
        }
        return Set.of(envDefaultProvider);
    }

    private NotificationProvider parseProvider(String rawValue, NotificationProvider fallback) {
        try {
            return NotificationProvider.fromRaw(rawValue);
        } catch (Exception _) {
            return fallback;
        }
    }
}
