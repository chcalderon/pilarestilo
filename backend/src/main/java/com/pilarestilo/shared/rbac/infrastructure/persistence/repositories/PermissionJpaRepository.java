package com.pilarestilo.shared.rbac.infrastructure.persistence.repositories;

import com.pilarestilo.shared.rbac.infrastructure.persistence.entities.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionJpaRepository extends JpaRepository<PermissionEntity, Long> {
}
