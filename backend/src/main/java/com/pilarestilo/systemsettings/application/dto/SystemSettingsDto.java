package com.pilarestilo.systemsettings.application.dto;

import java.time.Instant;

public record SystemSettingsDto(
        String whatsappNumber,
        String instagramUrl,
        String facebookUrl,
        String notificationProvider,
        String whatsappSimulatedTo,
        String whatsappSimulatedSender,
        String whatsappTwilioApiBaseUrl,
        String whatsappTwilioAccountSid,
        String whatsappTwilioFrom,
        String whatsappTwilioToFallback,
        String whatsappTwilioSenderAlias,
        boolean whatsappTwilioAuthTokenConfigured,
        String sendgridApiBaseUrl,
        String sendgridFromEmail,
        String sendgridSenderName,
        String sendgridToFallback,
        boolean sendgridApiKeyConfigured,
        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        String smtpFromEmail,
        boolean smtpAuthEnabled,
        boolean smtpStarttlsEnabled,
        boolean smtpPasswordConfigured,
        Instant updatedAt,
        String updatedBy
) {}
