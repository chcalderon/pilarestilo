package com.pilarestilo.shared.rbac.application.dto;

import com.pilarestilo.shared.rbac.domain.model.RolePermission;

import java.util.List;

public record PermissionMatrixDto(List<PermissionEntryDto> permissions) {

    public record PermissionEntryDto(String role, String viewKey) {}

    public static PermissionMatrixDto from(List<RolePermission> perms) {
        return new PermissionMatrixDto(
                perms.stream()
                        .map(p -> new PermissionEntryDto(p.getRole().name(), p.getViewKey()))
                        .toList());
    }
}
