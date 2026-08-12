package com.pilarestilo.notification.infrastructure.adapters;

import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SimulatedWhatsAppNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SimulatedWhatsAppNotificationSender.class);

    private final SystemSettingsRepository systemSettingsRepository;
    private final String envSimulatedTo;
    private final String envSenderAlias;

    public SimulatedWhatsAppNotificationSender(
            SystemSettingsRepository systemSettingsRepository,
            @Value("${app.notification.whatsapp.simulated-to:+56900000000}") String simulatedTo,
            @Value("${app.notification.whatsapp.simulated-sender:Pilar Estilo}") String senderAlias
    ) {
        this.systemSettingsRepository = systemSettingsRepository;
        this.envSimulatedTo = normalize(simulatedTo, "+56900000000");
        this.envSenderAlias = normalize(senderAlias, "Pilar Estilo");
    }

    private EffectiveConfig resolveConfig() {
        var settings = systemSettingsRepository.get();
        String simulatedTo = firstNonBlank(settings.getWhatsappSimulatedTo(), envSimulatedTo);
        String senderAlias = firstNonBlank(settings.getWhatsappSimulatedSender(), envSenderAlias);
        return new EffectiveConfig(
                normalize(simulatedTo, "+56900000000"),
                normalize(senderAlias, "Pilar Estilo")
        );
    }

    private void logSimulated(String template, String to, String senderAlias, UUID referenceId, NotificationRecipient recipient) {
        if (!recipient.allowsWhatsApp()) {
            log.info(
                    "[WHATSAPP:SIMULATED] skipped template={} referenceId={} reason=channel-preference preference={}",
                    template,
                    referenceId,
                    recipient.preference()
            );
            return;
        }

        log.info(
                "[WHATSAPP:SIMULATED] sender={} to={} template={} referenceId={} recipient={}",
                senderAlias,
                to,
                template,
                referenceId,
                recipient.preferredPhoneThenEmail()
        );
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

    @Override
    public void send(NotificationMessage message, NotificationRecipient recipient) {
        if (!recipient.allowsWhatsApp()) {
            log.info("[WHATSAPP:SIMULATED] skipped template={} reason=channel-preference",
                    message.templateKey());
            return;
        }
        log.info("[WHATSAPP:SIMULATED] template={} referenceId={} to={} body={}",
                message.templateKey(), message.referenceId(), recipient.phone(), message.bodyText());
    }
}
