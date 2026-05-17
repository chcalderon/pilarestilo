package com.pilarestilo.shared.rbac.infrastructure.persistence.repositories;

import com.pilarestilo.shared.rbac.domain.ports.RolePermissionGrantRepository;
import com.pilarestilo.user.domain.enums.UserRole;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RolePermissionGrantRepositoryAdapter implements RolePermissionGrantRepository {

    private final RolePermissionGrantJpaRepository jpaRepository;

    public RolePermissionGrantRepositoryAdapter(RolePermissionGrantJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<String> findPermissionCodesByRole(UserRole role) {
        return jpaRepository.findPermissionCodesByRole(role.name());
    }
}
