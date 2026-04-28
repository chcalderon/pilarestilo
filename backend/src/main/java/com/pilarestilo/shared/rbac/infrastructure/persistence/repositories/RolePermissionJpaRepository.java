package com.pilarestilo.shared.rbac.infrastructure.persistence.repositories;

import com.pilarestilo.shared.rbac.infrastructure.persistence.entities.RolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RolePermissionJpaRepository extends JpaRepository<RolePermissionEntity, Long> {

    @Query("SELECT e.viewKey FROM RolePermissionEntity e WHERE e.role = :role")
    List<String> findViewKeysByRole(String role);

    void deleteAllByRole(String role);
}
