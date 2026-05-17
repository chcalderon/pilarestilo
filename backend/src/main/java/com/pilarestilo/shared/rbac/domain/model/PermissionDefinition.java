package com.pilarestilo.shared.rbac.domain.model;

public record PermissionDefinition(
        String code,
        String name,
        String description,
        PermissionModule module,
        PermissionCategory category
) {
    public String authority() {
        return "PERM_" + code;
    }
}
