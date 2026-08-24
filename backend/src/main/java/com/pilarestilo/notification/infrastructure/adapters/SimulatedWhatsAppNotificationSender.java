package com.pilarestilo.notification.infrastructure.adapters;

import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    /**
     * Logs to the configured simulated destination, never to the customer.
     *
     * <p>That is the whole point of this adapter: it stands in for WhatsApp in environments
     * where messaging a real person would be wrong. An earlier version of this method logged
     * {@code recipient.phone()} and ignored the configured number and sender alias, which
     * defeated it — the settings existed and nothing read them.
     */
    // preferredPhoneThenEmail() is a trivial null/blank check, not worth an isInfoEnabled() guard.
    @SuppressWarnings("java:S2629")
    @Override
    public void send(NotificationMessage message, NotificationRecipient recipient) {
        if (!recipient.allowsWhatsApp()) {
            log.info(
                    "[WHATSAPP:SIMULATED] skipped template={} referenceId={} reason=channel-preference preference={}",
                    message.templateKey(),
                    message.referenceId(),
                    recipient.preference()
            );
            return;
        }

        EffectiveConfig config = resolveConfig();
        log.info(
                "[WHATSAPP:SIMULATED] sender={} to={} template={} referenceId={} recipient={} body={}",
                config.senderAlias(),
                config.simulatedTo(),
                message.templateKey(),
                message.referenceId(),
                recipient.preferredPhoneThenEmail(),
                message.bodyText()
        );
    }
}
