package com.pilarestilo.systemsettings.application.usecases;

import com.pilarestilo.systemsettings.application.commands.UpdateSystemSettingsCommand;
import com.pilarestilo.systemsettings.application.dto.SystemSettingsDto;
import com.pilarestilo.systemsettings.application.mappers.SystemSettingsMapper;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateSystemSettingsUseCase {

    private final SystemSettingsRepository systemSettingsRepository;
    private final SystemSettingsCryptoService cryptoService;

    public UpdateSystemSettingsUseCase(
            SystemSettingsRepository systemSettingsRepository,
            SystemSettingsCryptoService cryptoService
    ) {
        this.systemSettingsRepository = systemSettingsRepository;
        this.cryptoService = cryptoService;
    }

    @Transactional
    public SystemSettingsDto execute(UpdateSystemSettingsCommand command) {
        var settings = systemSettingsRepository.get();

        String nextEncryptedPassword = settings.getSmtpPasswordEncrypted();
        if (Boolean.TRUE.equals(command.clearSmtpPassword())) {
            nextEncryptedPassword = null;
        } else if (command.smtpPassword() != null && !command.smtpPassword().isBlank()) {
            nextEncryptedPassword = cryptoService.encrypt(command.smtpPassword().trim());
        }

        settings.update(
                command.whatsappNumber(),
                command.instagramUrl(),
                command.facebookUrl(),
                command.smtpHost(),
                command.smtpPort(),
                command.smtpUsername(),
                command.smtpFromEmail(),
                nextEncryptedPassword,
                Boolean.TRUE.equals(command.smtpAuthEnabled()),
                Boolean.TRUE.equals(command.smtpStarttlsEnabled()),
                command.updatedBy()
        );

        var saved = systemSettingsRepository.save(settings);
        return SystemSettingsMapper.toDto(saved);
    }
}
