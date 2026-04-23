package com.pilarestilo.systemsettings.application.usecases;

import com.pilarestilo.systemsettings.application.dto.SystemSettingsDto;
import com.pilarestilo.systemsettings.application.mappers.SystemSettingsMapper;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetSystemSettingsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetSystemSettingsUseCase.class);

    private final SystemSettingsRepository systemSettingsRepository;
    private final SystemSettingsCryptoService cryptoService;

    public GetSystemSettingsUseCase(
            SystemSettingsRepository systemSettingsRepository,
            SystemSettingsCryptoService cryptoService
    ) {
        this.systemSettingsRepository = systemSettingsRepository;
        this.cryptoService = cryptoService;
    }

    @Transactional(readOnly = true)
    public SystemSettingsDto execute() {
        var settings = systemSettingsRepository.get();
        verifyDecryptable(settings.getSmtpPasswordEncrypted(), "SMTP password");
        verifyDecryptable(settings.getWhatsappTwilioAuthTokenEncrypted(), "Twilio auth token");
        verifyDecryptable(settings.getSendgridApiKeyEncrypted(), "SendGrid API key");
        verifyDecryptable(settings.getMediaS3SecretKeyEncrypted(), "Media S3 secret key");
        return SystemSettingsMapper.toDto(settings);
    }

    private void verifyDecryptable(String encryptedValue, String label) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            return;
        }
        try {
            cryptoService.decrypt(encryptedValue);
        } catch (DomainException ex) {
            // Keep settings readable even if old/corrupted encrypted values exist.
            // Admin can then replace or clear values from the UI.
            log.warn("{} decrypt check failed while loading system settings: {}", label, ex.getMessage());
        }
    }
}
