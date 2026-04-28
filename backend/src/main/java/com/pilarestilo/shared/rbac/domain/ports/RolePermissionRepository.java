package com.pilarestilo.shared.rbac.domain.ports;

import com.pilarestilo.shared.rbac.domain.model.RolePermission;
import com.pilarestilo.user.domain.enums.UserRole;

import java.util.List;

public interface RolePermissionRepository {
    List<String> findViewKeysByRole(UserRole role);
    List<RolePermission> findAll();
    void replaceAll(List<RolePermission> permissions);
}
