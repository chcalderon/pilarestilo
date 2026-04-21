package com.pilarestilo.systemsettings.application.usecases;

import com.pilarestilo.systemsettings.application.dto.PublicStoreSettingsDto;
import com.pilarestilo.systemsettings.application.mappers.SystemSettingsMapper;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetPublicStoreSettingsUseCase {

    private final SystemSettingsRepository systemSettingsRepository;

    public GetPublicStoreSettingsUseCase(SystemSettingsRepository systemSettingsRepository) {
        this.systemSettingsRepository = systemSettingsRepository;
    }

    @Transactional(readOnly = true)
    public PublicStoreSettingsDto execute() {
        return SystemSettingsMapper.toPublicDto(systemSettingsRepository.get());
    }
}
