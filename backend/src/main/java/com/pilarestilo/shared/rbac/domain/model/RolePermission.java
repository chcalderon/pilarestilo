package com.pilarestilo.shared.rbac.domain.model;

import com.pilarestilo.user.domain.enums.UserRole;

import java.util.Objects;

public class RolePermission {

    private final UserRole role;
    private final String viewKey;

    public RolePermission(UserRole role, String viewKey) {
        this.role = role;
        this.viewKey = viewKey;
    }

    public UserRole getRole() { return role; }
    public String getViewKey() { return viewKey; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RolePermission other)) return false;
        return role == other.role && Objects.equals(viewKey, other.viewKey);
    }

    @Override
    public int hashCode() { return Objects.hash(role, viewKey); }
}
