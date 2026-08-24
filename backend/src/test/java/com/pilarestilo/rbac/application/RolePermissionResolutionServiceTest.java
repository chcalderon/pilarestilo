package com.pilarestilo.rbac.application;

import com.pilarestilo.shared.rbac.application.LegacyViewPermissionMapper;
import com.pilarestilo.shared.rbac.application.RolePermissionResolutionService;
import com.pilarestilo.shared.rbac.domain.model.ResolvedPermissions;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionGrantRepository;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionRepository;
import com.pilarestilo.shared.rbac.domain.model.RolePermission;
import com.pilarestilo.user.domain.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolePermissionResolutionServiceTest {

    @Test
    void resolves_grants_plus_legacy_mapping_without_duplicates() {
        InMemoryRolePermissionRepository legacyRepo = new InMemoryRolePermissionRepository();
        legacyRepo.viewKeys.put(UserRole.SELLER, List.of("dashboard", "productos", "caja"));

        InMemoryRolePermissionGrantRepository grantRepo = new InMemoryRolePermissionGrantRepository();
        grantRepo.permissionCodes.put(UserRole.SELLER, List.of("products.read", "cash.read", "pos.sale.create"));

        RolePermissionResolutionService service = new RolePermissionResolutionService(
                legacyRepo,
                grantRepo,
                new LegacyViewPermissionMapper()
        );

        ResolvedPermissions resolved = service.resolve(UserRole.SELLER);

        assertEquals(List.of("dashboard", "productos", "caja"), resolved.legacyViewKeys());
        assertEquals(List.of(
                "products.read",
                "cash.read",
                "pos.sale.create",
                "dashboard.read",
                "analytics.read",
                "categories.read",
                "navigation.read",
                "reviews.read",
                "discounts.read",
                "publications.read",
                "payments.read"
        ), resolved.permissionCodes());
    }

    @Test
    void cache_is_invalidable_per_role() {
        InMemoryRolePermissionRepository legacyRepo = new InMemoryRolePermissionRepository();
        legacyRepo.viewKeys.put(UserRole.SUPERVISOR, List.of("dashboard"));

        InMemoryRolePermissionGrantRepository grantRepo = new InMemoryRolePermissionGrantRepository();
        grantRepo.permissionCodes.put(UserRole.SUPERVISOR, new ArrayList<>(List.of("cash.read")));

        RolePermissionResolutionService service = new RolePermissionResolutionService(
                legacyRepo,
                grantRepo,
                new LegacyViewPermissionMapper()
        );

        assertTrue(service.resolve(UserRole.SUPERVISOR).permissionCodes().contains("cash.read"));

        grantRepo.permissionCodes.put(UserRole.SUPERVISOR, List.of("cash.close"));
        assertTrue(service.resolve(UserRole.SUPERVISOR).permissionCodes().contains("cash.read"));

        service.invalidate(UserRole.SUPERVISOR);

        assertTrue(service.resolve(UserRole.SUPERVISOR).permissionCodes().contains("cash.close"));
    }

    private static final class InMemoryRolePermissionRepository implements RolePermissionRepository {
        private final Map<UserRole, List<String>> viewKeys = new EnumMap<>(UserRole.class);

        @Override
        public List<String> findViewKeysByRole(UserRole role) {
            return viewKeys.getOrDefault(role, List.of());
        }

        @Override
        public List<RolePermission> findAll() {
            return List.of();
        }

        @Override
        public void replaceAll(List<RolePermission> permissions) {
            // Not exercised by this test's scenarios; the fake only needs to satisfy the interface.
        }
    }

    private static final class InMemoryRolePermissionGrantRepository implements RolePermissionGrantRepository {
        private final Map<UserRole, List<String>> permissionCodes = new EnumMap<>(UserRole.class);

        @Override
        public List<String> findPermissionCodesByRole(UserRole role) {
            return permissionCodes.getOrDefault(role, List.of());
        }
    }
}
