package com.pilarestilo.systemsettings.application.mappers;

import com.pilarestilo.systemsettings.application.dto.PublicStoreSettingsDto;
import com.pilarestilo.systemsettings.application.dto.SystemSettingsDto;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;

public final class SystemSettingsMapper {

    private SystemSettingsMapper() {}

    public static SystemSettingsDto toDto(SystemSettings settings) {
        return new SystemSettingsDto(
                settings.getWhatsappNumber(),
                settings.getInstagramUrl(),
                settings.getFacebookUrl(),
                settings.getSmtpHost(),
                settings.getSmtpPort(),
                settings.getSmtpUsername(),
                settings.getSmtpFromEmail(),
                settings.isSmtpAuthEnabled(),
                settings.isSmtpStarttlsEnabled(),
                settings.getSmtpPasswordEncrypted() != null && !settings.getSmtpPasswordEncrypted().isBlank(),
                settings.getUpdatedAt(),
                settings.getUpdatedBy()
        );
    }

    public static PublicStoreSettingsDto toPublicDto(SystemSettings settings) {
        return new PublicStoreSettingsDto(
                settings.getWhatsappNumber(),
                settings.getInstagramUrl(),
                settings.getFacebookUrl()
        );
    }
}
