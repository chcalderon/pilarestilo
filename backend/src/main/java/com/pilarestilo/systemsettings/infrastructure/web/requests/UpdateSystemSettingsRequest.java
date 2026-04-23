package com.pilarestilo.systemsettings.infrastructure.web.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSystemSettingsRequest(
        @NotBlank @Size(max = 40) String whatsappNumber,
        @Size(max = 500) String instagramUrl,
        @Size(max = 500) String facebookUrl,
        @NotBlank @Size(max = 40) String mediaStorageProvider,
        @Size(max = 255) String mediaS3Endpoint,
        @Size(max = 120) String mediaS3Region,
        @Size(max = 120) String mediaS3Bucket,
        @Size(max = 255) String mediaS3AccessKeyId,
        @Size(max = 255) String mediaS3SecretKey,
        Boolean clearMediaS3SecretKey,
        @NotNull Boolean mediaS3PathStyleEnabled,
        @Size(max = 500) String mediaS3PublicBaseUrl,
        @NotBlank @Size(max = 40) String notificationProvider,
        @Size(max = 40) String whatsappSimulatedTo,
        @Size(max = 120) String whatsappSimulatedSender,
        @Size(max = 255) String whatsappTwilioApiBaseUrl,
        @Size(max = 255) String whatsappTwilioAccountSid,
        @Size(max = 80) String whatsappTwilioFrom,
        @Size(max = 80) String whatsappTwilioToFallback,
        @Size(max = 120) String whatsappTwilioSenderAlias,
        @Size(max = 255) String whatsappTwilioAuthToken,
        Boolean clearWhatsappTwilioAuthToken,
        @Size(max = 255) String sendgridApiBaseUrl,
        @Size(max = 255) String sendgridFromEmail,
        @Size(max = 120) String sendgridSenderName,
        @Size(max = 255) String sendgridToFallback,
        @Size(max = 255) String sendgridApiKey,
        Boolean clearSendgridApiKey,
        @Size(max = 255) String smtpHost,
        @Min(1) @Max(65535) Integer smtpPort,
        @Size(max = 255) String smtpUsername,
        @Size(max = 255) String smtpFromEmail,
        @Size(max = 255) String smtpPassword,
        Boolean clearSmtpPassword,
        @NotNull Boolean smtpAuthEnabled,
        @NotNull Boolean smtpStarttlsEnabled
) {}
