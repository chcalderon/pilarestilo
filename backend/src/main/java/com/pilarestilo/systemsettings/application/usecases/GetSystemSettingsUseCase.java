package com.pilarestilo.systemsettings.application.usecases;

import com.pilarestilo.systemsettings.application.dto.SystemSettingsDto;
import com.pilarestilo.systemsettings.application.mappers.SystemSettingsMapper;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetSystemSettingsUseCase {

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
        if (settings.getSmtpPasswordEncrypted() != null && !settings.getSmtpPasswordEncrypted().isBlank()) {
            // Validate decryptability so corrupted values are surfaced immediately.
            cryptoService.decrypt(settings.getSmtpPasswordEncrypted());
        }
        return SystemSettingsMapper.toDto(settings);
    }
}
