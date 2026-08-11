package com.pilarestilo.systemsettings.application.usecases;

import com.pilarestilo.systemsettings.application.dto.PublicStoreSettingsDto;
import com.pilarestilo.systemsettings.application.mappers.SystemSettingsMapper;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.shared.infrastructure.cache.CacheNames;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetPublicStoreSettingsUseCase {

    private final SystemSettingsRepository systemSettingsRepository;
    /**
     * Read straight from configuration rather than through the order module's remote client:
     * the storefront only needs to know whether a code can be accepted, and routing that
     * through another module's bean would couple settings to the order package.
     */
    private final boolean discountCodesEnabled;

    public GetPublicStoreSettingsUseCase(
            SystemSettingsRepository systemSettingsRepository,
            @Value("${app.order.remote.write-enabled:false}") boolean orderWritesAreRemote) {
        this.systemSettingsRepository = systemSettingsRepository;
        this.discountCodesEnabled = !orderWritesAreRemote;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PUBLIC_STORE_SETTINGS, sync = true)
    public PublicStoreSettingsDto execute() {
        return SystemSettingsMapper.toPublicDto(systemSettingsRepository.get(), discountCodesEnabled);
    }
}
