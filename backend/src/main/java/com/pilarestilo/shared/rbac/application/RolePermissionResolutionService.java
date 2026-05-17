package com.pilarestilo.shared.rbac.application;

import com.pilarestilo.shared.rbac.domain.model.ResolvedPermissions;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionGrantRepository;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionRepository;
import com.pilarestilo.user.domain.enums.UserRole;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RolePermissionResolutionService {

    private final RolePermissionRepository legacyRepository;
    private final RolePermissionGrantRepository grantRepository;
    private final LegacyViewPermissionMapper legacyMapper;
    private final ConcurrentMap<UserRole, ResolvedPermissions> cache = new ConcurrentHashMap<>();

    public RolePermissionResolutionService(
            RolePermissionRepository legacyRepository,
            RolePermissionGrantRepository grantRepository,
            LegacyViewPermissionMapper legacyMapper
    ) {
        this.legacyRepository = legacyRepository;
        this.grantRepository = grantRepository;
        this.legacyMapper = legacyMapper;
    }

    public ResolvedPermissions resolve(UserRole role) {
        return cache.computeIfAbsent(role, this::loadPermissions);
    }

    public void invalidate(UserRole role) {
        cache.remove(role);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private ResolvedPermissions loadPermissions(UserRole role) {
        List<String> legacyViewKeys = legacyRepository.findViewKeysByRole(role);
        List<String> mappedLegacyCodes = legacyMapper.toPermissionCodes(legacyViewKeys);
        List<String> explicitCodes = grantRepository.findPermissionCodesByRole(role);

        LinkedHashSet<String> permissionCodes = new LinkedHashSet<>(explicitCodes);
        permissionCodes.addAll(mappedLegacyCodes);

        return new ResolvedPermissions(legacyViewKeys, List.copyOf(permissionCodes));
    }
}
