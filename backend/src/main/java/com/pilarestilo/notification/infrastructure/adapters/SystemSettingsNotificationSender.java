package com.pilarestilo.notification.infrastructure.adapters;

import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.systemsettings.domain.enums.NotificationProvider;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Primary
public class SystemSettingsNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingsNotificationSender.class);

    private final SystemSettingsRepository systemSettingsRepository;
    private final LogNotificationSender logNotificationSender;
    private final SimulatedWhatsAppNotificationSender simulatedWhatsAppNotificationSender;
    private final TwilioWhatsAppNotificationSender twilioWhatsAppNotificationSender;
    private final SendGridEmailNotificationSender sendGridEmailNotificationSender;
    private final SmtpEmailNotificationSender smtpEmailNotificationSender;
    private final N8nWebhookNotificationSender n8nWebhookNotificationSender;
    private final NotificationProvider envDefaultProvider;

    public SystemSettingsNotificationSender(
            SystemSettingsRepository systemSettingsRepository,
            LogNotificationSender logNotificationSender,
            SimulatedWhatsAppNotificationSender simulatedWhatsAppNotificationSender,
            TwilioWhatsAppNotificationSender twilioWhatsAppNotificationSender,
            SendGridEmailNotificationSender sendGridEmailNotificationSender,
            SmtpEmailNotificationSender smtpEmailNotificationSender,
            N8nWebhookNotificationSender n8nWebhookNotificationSender,
            @Value("${app.notification.provider:LOG}") String envDefaultProvider
    ) {
        this.systemSettingsRepository = systemSettingsRepository;
        this.logNotificationSender = logNotificationSender;
        this.simulatedWhatsAppNotificationSender = simulatedWhatsAppNotificationSender;
        this.twilioWhatsAppNotificationSender = twilioWhatsAppNotificationSender;
        this.sendGridEmailNotificationSender = sendGridEmailNotificationSender;
        this.smtpEmailNotificationSender = smtpEmailNotificationSender;
        this.n8nWebhookNotificationSender = n8nWebhookNotificationSender;
        this.envDefaultProvider = parseProvider(envDefaultProvider, NotificationProvider.LOG);
    }

    /**
     * Sends over every channel the shop has enabled, not one of them.
     *
     * <p>This used to switch on a single provider, so turning on WhatsApp silently stopped every
     * email — including the transfer instructions and the written confirmation the Ley 21.398
     * requires. A shop notifies over the channels it uses, usually more than one.
     *
     * <p>Each channel is attempted on its own. One provider throwing — a dead SMTP host, a Twilio
     * outage — must not swallow the others, because the customer only needs to hear once and the
     * shop has no way of knowing which channel reached her.
     */
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
            Set<NotificationProvider> configured = systemSettingsRepository.get().getNotificationProviders();
            if (configured != null && !configured.isEmpty()) {
                /*
                 * A seeded row still on LOG is not a decision anybody made, so the environment gets
                 * to speak. Once an admin saves the panel the stored set wins, LOG included.
                 */
                if (configured.equals(Set.of(NotificationProvider.LOG)) && seededBySystem()) {
                    return Set.of(envDefaultProvider);
                }
                return configured;
            }
        } catch (Exception ex) {
            log.warn("Could not read notification providers from system settings, using env fallback: {}",
                    ex.getMessage());
        }
        return Set.of(envDefaultProvider);
    }

    private boolean seededBySystem() {
        String updatedBy = systemSettingsRepository.get().getUpdatedBy();
        return updatedBy != null && updatedBy.startsWith("system-");
    }

    private NotificationProvider parseProvider(String rawValue, NotificationProvider fallback) {
        try {
            return NotificationProvider.fromRaw(rawValue);
        } catch (Exception _) {
            return fallback;
        }
    }
}
