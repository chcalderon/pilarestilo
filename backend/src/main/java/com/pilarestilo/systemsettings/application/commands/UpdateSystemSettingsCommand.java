package com.pilarestilo.systemsettings.application.commands;

public record UpdateSystemSettingsCommand(
        String whatsappNumber,
        String instagramUrl,
        String facebookUrl,
        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        String smtpFromEmail,
        String smtpPassword,
        Boolean clearSmtpPassword,
        Boolean smtpAuthEnabled,
        Boolean smtpStarttlsEnabled,
        String updatedBy
) {}
