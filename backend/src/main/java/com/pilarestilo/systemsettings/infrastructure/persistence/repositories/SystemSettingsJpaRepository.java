package com.pilarestilo.systemsettings.infrastructure.persistence.repositories;

import com.pilarestilo.systemsettings.infrastructure.persistence.entities.SystemSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingsJpaRepository extends JpaRepository<SystemSettingsEntity, Short> {
}
