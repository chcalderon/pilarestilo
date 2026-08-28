package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import com.pilarestilo.notificationservice.domain.ports.MessagingSettingsPort;
import com.pilarestilo.notificationservice.domain.view.MessagingSettings;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class MessagingSettingsAdapter implements MessagingSettingsPort {

    /** The single {@code system_settings} row. */
    private static final short SETTINGS_ID = 1;

    private static final MessagingSettings EMPTY = new MessagingSettings(
            List.of(), null,
            null, null, null, null, null, false, false,
            null, null, null, null, null,
            null, null, null, null, null, null,
            null, null,
            null, null, null,
            false, 0);

    private final SystemSettingsRoRepository repository;

    public MessagingSettingsAdapter(SystemSettingsRoRepository repository) {
        this.repository = repository;
    }

    @Override
    public MessagingSettings current() {
        return repository.findById(SETTINGS_ID).map(MessagingSettingsAdapter::toView).orElse(EMPTY);
    }

    private static MessagingSettings toView(SystemSettingsRoEntity e) {
        return new MessagingSettings(
                splitProviders(e.getNotificationProviders()),
                e.getUpdatedBy(),
                e.getSmtpHost(), e.getSmtpPort(), e.getSmtpUsername(), e.getSmtpFromEmail(),
                e.getSmtpPasswordEncrypted(), e.isSmtpAuthEnabled(), e.isSmtpStarttlsEnabled(),
                e.getSendgridApiBaseUrl(), e.getSendgridApiKeyEncrypted(), e.getSendgridFromEmail(),
                e.getSendgridSenderName(), e.getSendgridToFallback(),
                e.getWhatsappTwilioApiBaseUrl(), e.getWhatsappTwilioAccountSid(),
                e.getWhatsappTwilioAuthTokenEncrypted(), e.getWhatsappTwilioFrom(),
                e.getWhatsappTwilioToFallback(), e.getWhatsappTwilioSenderAlias(),
                e.getWhatsappSimulatedTo(), e.getWhatsappSimulatedSender(),
                e.getN8nWebhookUrl(), e.getN8nApiKeyEncrypted(), e.getN8nTokenHeaderName(),
                e.isBankTransferAutoCancelEnabled(), e.getBankTransferAutoCancelTimeoutMinutes());
    }

    private static List<String> splitProviders(String commaJoined) {
        if (commaJoined == null || commaJoined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(commaJoined.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
