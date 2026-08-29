package com.pilarestilo.notificationservice.infrastructure.adapters;

import com.pilarestilo.notificationservice.domain.model.NotificationMessage;
import com.pilarestilo.notificationservice.domain.model.NotificationRecipient;
import com.pilarestilo.notificationservice.domain.ports.MessagingSettingsPort;
import com.pilarestilo.notificationservice.domain.ports.NotificationSender;
import com.pilarestilo.notificationservice.domain.view.MessagingSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SimulatedWhatsAppNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SimulatedWhatsAppNotificationSender.class);

    private final MessagingSettingsPort messagingSettings;
    private final String envSimulatedTo;
    private final String envSenderAlias;

    public SimulatedWhatsAppNotificationSender(
            MessagingSettingsPort messagingSettings,
            @Value("${app.notification.whatsapp.simulated-to:+56900000000}") String simulatedTo,
            @Value("${app.notification.whatsapp.simulated-sender:Pilar Estilo}") String senderAlias
    ) {
        this.messagingSettings = messagingSettings;
        this.envSimulatedTo = normalize(simulatedTo, "+56900000000");
        this.envSenderAlias = normalize(senderAlias, "Pilar Estilo");
    }

    private EffectiveConfig resolveConfig() {
        MessagingSettings settings = messagingSettings.current();
        String simulatedTo = firstNonBlank(settings.simulatedTo(), envSimulatedTo);
        String senderAlias = firstNonBlank(settings.simulatedSender(), envSenderAlias);
        return new EffectiveConfig(
                normalize(simulatedTo, "+56900000000"),
                normalize(senderAlias, "Pilar Estilo"));
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private record EffectiveConfig(String simulatedTo, String senderAlias) {}

    @SuppressWarnings("java:S2629")
    @Override
    public void send(NotificationMessage message, NotificationRecipient recipient) {
        if (!recipient.allowsWhatsApp()) {
            log.info("[WHATSAPP:SIMULATED] skipped template={} referenceId={} reason=channel-preference preference={}",
                    message.templateKey(), message.referenceId(), recipient.preference());
            return;
        }

        EffectiveConfig config = resolveConfig();
        log.info("[WHATSAPP:SIMULATED] sender={} to={} template={} referenceId={} recipient={} body={}",
                config.senderAlias(), config.simulatedTo(), message.templateKey(),
                message.referenceId(), recipient.preferredPhoneThenEmail(), message.bodyText());
    }
}
