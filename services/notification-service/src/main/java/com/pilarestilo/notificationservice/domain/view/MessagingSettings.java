package com.pilarestilo.notificationservice.domain.view;

import java.util.List;

/**
 * The shop's messaging configuration, read from {@code system_settings} on the shared database.
 *
 * <p>Secrets are held as their stored ciphertext ({@code *Encrypted}); the senders decrypt them
 * locally with {@code SystemSettingsCryptoService}, exactly as the monolith does — an internal
 * endpoint returning plaintext was rejected for this reason.
 */
public record MessagingSettings(
        List<String> notificationProviders,
        String updatedBy,

        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        String smtpFromEmail,
        String smtpPasswordEncrypted,
        boolean smtpAuthEnabled,
        boolean smtpStarttlsEnabled,

        String sendgridApiBaseUrl,
        String sendgridApiKeyEncrypted,
        String sendgridFromEmail,
        String sendgridSenderName,
        String sendgridToFallback,

        String twilioApiBaseUrl,
        String twilioAccountSid,
        String twilioAuthTokenEncrypted,
        String twilioFrom,
        String twilioToFallback,
        String twilioSenderAlias,

        String simulatedTo,
        String simulatedSender,

        String n8nWebhookUrl,
        String n8nApiKeyEncrypted,
        String n8nTokenHeaderName,

        boolean bankTransferAutoCancelEnabled,
        int bankTransferAutoCancelTimeoutMinutes) {

    public boolean seededBySystem() {
        return updatedBy != null && updatedBy.startsWith("system-");
    }

    /** Every field null/blank/false — a {@code system_settings} row with nothing configured. */
    public static MessagingSettings empty() {
        return new MessagingSettings(
                List.of(), null,
                null, null, null, null, null, false, false,
                null, null, null, null, null,
                null, null, null, null, null, null,
                null, null,
                null, null, null,
                false, 0);
    }
}
