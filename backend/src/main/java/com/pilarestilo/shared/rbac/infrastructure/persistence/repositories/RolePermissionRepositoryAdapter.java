package com.pilarestilo.shared.rbac.infrastructure.persistence.repositories;

import com.pilarestilo.shared.rbac.domain.model.RolePermission;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionRepository;
import com.pilarestilo.shared.rbac.infrastructure.persistence.entities.RolePermissionEntity;
import com.pilarestilo.user.domain.enums.UserRole;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class RolePermissionRepositoryAdapter implements RolePermissionRepository {

    private final RolePermissionJpaRepository jpaRepository;

    public RolePermissionRepositoryAdapter(RolePermissionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<String> findViewKeysByRole(UserRole role) {
        return jpaRepository.findViewKeysByRole(role.name());
    }

    @Override
    public List<RolePermission> findAll() {
        return jpaRepository.findAll().stream()
                .map(e -> new RolePermission(UserRole.valueOf(e.getRole()), e.getViewKey()))
                .toList();
    }

    @Override
    @Transactional
    public void replaceAll(List<RolePermission> permissions) {
        List<UserRole> editableRoles = List.of(
                UserRole.SUPERVISOR, UserRole.ADMINISTRACION, UserRole.DESPACHADOR, UserRole.SELLER);
        for (UserRole role : editableRoles) {
            jpaRepository.deleteAllByRole(role.name());
        }
        List<RolePermissionEntity> entities = permissions.stream()
                .filter(p -> editableRoles.contains(p.getRole()))
                .map(p -> {
                    RolePermissionEntity e = new RolePermissionEntity();
                    e.setRole(p.getRole().name());
                    e.setViewKey(p.getViewKey());
                    return e;
                }).toList();
        jpaRepository.saveAll(entities);
    }
}
