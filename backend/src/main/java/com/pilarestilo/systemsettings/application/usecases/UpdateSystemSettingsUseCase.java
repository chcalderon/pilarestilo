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

        String nextSmtpPassword = resolveEncryptedSecret(
                settings.getSmtpPasswordEncrypted(),
                command.smtpPassword(),
                command.clearSmtpPassword()
        );
        String nextTwilioAuthToken = resolveEncryptedSecret(
                settings.getWhatsappTwilioAuthTokenEncrypted(),
                command.whatsappTwilioAuthToken(),
                command.clearWhatsappTwilioAuthToken()
        );
        String nextSendgridApiKey = resolveEncryptedSecret(
                settings.getSendgridApiKeyEncrypted(),
                command.sendgridApiKey(),
                command.clearSendgridApiKey()
        );

        settings.update(
                command.whatsappNumber(),
                command.instagramUrl(),
                command.facebookUrl(),
                command.smtpHost(),
                command.smtpPort(),
                command.smtpUsername(),
                command.smtpFromEmail(),
                nextSmtpPassword,
                Boolean.TRUE.equals(command.smtpAuthEnabled()),
                Boolean.TRUE.equals(command.smtpStarttlsEnabled()),
                command.notificationProvider(),
                command.whatsappSimulatedTo(),
                command.whatsappSimulatedSender(),
                command.whatsappTwilioApiBaseUrl(),
                command.whatsappTwilioAccountSid(),
                nextTwilioAuthToken,
                command.whatsappTwilioFrom(),
                command.whatsappTwilioToFallback(),
                command.whatsappTwilioSenderAlias(),
                command.sendgridApiBaseUrl(),
                nextSendgridApiKey,
                command.sendgridFromEmail(),
                command.sendgridSenderName(),
                command.sendgridToFallback(),
                command.updatedBy()
        );

        var saved = systemSettingsRepository.save(settings);
        return SystemSettingsMapper.toDto(saved);
    }

    private String resolveEncryptedSecret(String currentEncrypted, String nextPlainText, Boolean clearFlag) {
        if (Boolean.TRUE.equals(clearFlag)) {
            return null;
        }
        if (nextPlainText != null && !nextPlainText.isBlank()) {
            return cryptoService.encrypt(nextPlainText.trim());
        }
        return currentEncrypted;
    }
}
