package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingsRoRepository extends JpaRepository<SystemSettingsRoEntity, Short> {
}
