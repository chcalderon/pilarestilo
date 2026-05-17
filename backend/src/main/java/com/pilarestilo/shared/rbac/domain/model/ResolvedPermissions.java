package com.pilarestilo.shared.rbac.domain.model;

import java.util.List;

public record ResolvedPermissions(List<String> legacyViewKeys, List<String> permissionCodes) {

    public ResolvedPermissions {
        legacyViewKeys = List.copyOf(legacyViewKeys);
        permissionCodes = List.copyOf(permissionCodes);
    }

    public static ResolvedPermissions empty() {
        return new ResolvedPermissions(List.of(), List.of());
    }
}
