package com.pilarestilo.shared.rbac.domain.ports;

import com.pilarestilo.user.domain.enums.UserRole;

import java.util.List;

public interface RolePermissionGrantRepository {
    List<String> findPermissionCodesByRole(UserRole role);
}
