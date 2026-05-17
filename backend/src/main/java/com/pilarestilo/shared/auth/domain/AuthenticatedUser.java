package com.pilarestilo.shared.auth.domain;

import com.pilarestilo.user.domain.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String email,
        UserRole role,
        List<String> permissions,
        List<String> permissionCodes
) {
    public AuthenticatedUser {
        permissions = List.copyOf(permissions);
        permissionCodes = List.copyOf(permissionCodes);
    }

    public List<GrantedAuthority> toAuthorities() {
        LinkedHashSet<String> authorityCodes = new LinkedHashSet<>();
        authorityCodes.add("ROLE_" + role.name());
        for (String permissionCode : permissionCodes) {
            authorityCodes.add("PERM_" + permissionCode);
        }
        return authorityCodes.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
