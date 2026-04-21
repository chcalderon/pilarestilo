package com.pilarestilo.systemsettings.domain.ports;

import com.pilarestilo.systemsettings.domain.model.SystemSettings;

public interface SystemSettingsRepository {
    SystemSettings get();
    SystemSettings save(SystemSettings settings);
}
