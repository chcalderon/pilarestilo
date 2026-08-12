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

    @Override
    public void send(NotificationMessage message, NotificationRecipient recipient) {
        resolveSender().send(message, recipient);
    }

    private NotificationSender resolveSender() {
        NotificationProvider provider = resolveProvider();
        return switch (provider) {
            case WHATSAPP_SIMULATED -> simulatedWhatsAppNotificationSender;
            case WHATSAPP_TWILIO -> twilioWhatsAppNotificationSender;
            case EMAIL_SENDGRID -> sendGridEmailNotificationSender;
            case EMAIL_SMTP -> smtpEmailNotificationSender;
            case N8N_WEBHOOK -> n8nWebhookNotificationSender;
            case LOG -> logNotificationSender;
        };
    }

    private NotificationProvider resolveProvider() {
        try {
            var settings = systemSettingsRepository.get();
            if (settings.getNotificationProvider() != null) {
                if (
                        settings.getNotificationProvider() == NotificationProvider.LOG
                                && settings.getUpdatedBy() != null
                                && settings.getUpdatedBy().startsWith("system-")
                ) {
                    return envDefaultProvider;
                }
                return settings.getNotificationProvider();
            }
        } catch (Exception ex) {
            log.warn("Could not read notification provider from system settings, using env fallback: {}", ex.getMessage());
        }
        return envDefaultProvider;
    }

    private NotificationProvider parseProvider(String rawValue, NotificationProvider fallback) {
        try {
            return NotificationProvider.fromRaw(rawValue);
        } catch (Exception ex) {
            return fallback;
        }
    }
}
