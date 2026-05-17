package com.pilarestilo.shared.rbac.infrastructure.persistence.repositories;

import com.pilarestilo.shared.rbac.infrastructure.persistence.entities.RolePermissionGrantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RolePermissionGrantJpaRepository extends JpaRepository<RolePermissionGrantEntity, Long> {

    @Query(value = """
            SELECT g.permission_code
            FROM role_permission_grants g
            JOIN permissions p ON p.code = g.permission_code
            WHERE g.role = :role
              AND p.active = TRUE
            ORDER BY g.permission_code
            """, nativeQuery = true)
    List<String> findPermissionCodesByRole(String role);
}
