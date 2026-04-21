package com.pilarestilo.systemsettings.application.dto;

import java.time.Instant;

public record SystemSettingsDto(
        String whatsappNumber,
        String instagramUrl,
        String facebookUrl,
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
