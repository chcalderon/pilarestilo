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
        @Size(max = 255) String smtpHost,
        @Min(1) @Max(65535) Integer smtpPort,
        @Size(max = 255) String smtpUsername,
        @Size(max = 255) String smtpFromEmail,
        @Size(max = 255) String smtpPassword,
        Boolean clearSmtpPassword,
        @NotNull Boolean smtpAuthEnabled,
        @NotNull Boolean smtpStarttlsEnabled
) {}
