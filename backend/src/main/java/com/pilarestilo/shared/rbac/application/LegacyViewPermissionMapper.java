package com.pilarestilo.shared.rbac.application;

import com.pilarestilo.shared.rbac.domain.PermissionRegistry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class LegacyViewPermissionMapper {

    private static final Map<String, List<String>> MAPPING = createMapping();

    public List<String> toPermissionCodes(List<String> viewKeys) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String viewKey : viewKeys) {
            codes.addAll(MAPPING.getOrDefault(viewKey, List.of()));
        }
        return List.copyOf(codes);
    }

    private static Map<String, List<String>> createMapping() {
        Map<String, List<String>> mapping = new LinkedHashMap<>();
        mapping.put("dashboard", List.of(
                PermissionRegistry.DASHBOARD_READ.code(),
                PermissionRegistry.ANALYTICS_READ.code()
        ));
        mapping.put("productos", List.of(
                PermissionRegistry.PRODUCTS_READ.code(),
                PermissionRegistry.CATEGORIES_READ.code(),
                PermissionRegistry.NAVIGATION_READ.code(),
                PermissionRegistry.REVIEWS_READ.code(),
                PermissionRegistry.DISCOUNTS_READ.code(),
                PermissionRegistry.PUBLICATIONS_READ.code()
        ));
        mapping.put("usuarios", List.of(
                PermissionRegistry.USERS_READ.code()
        ));
        mapping.put("caja", List.of(
                PermissionRegistry.CASH_READ.code(),
                PermissionRegistry.PAYMENTS_READ.code()
        ));
        mapping.put("despachos", List.of(
                PermissionRegistry.DISPATCH_READ.code(),
                PermissionRegistry.DISPATCH_CLAIM.code(),
                PermissionRegistry.DISPATCH_MANAGE.code()
        ));
        mapping.put("configuracion", List.of(
                PermissionRegistry.SETTINGS_READ.code()
        ));
        mapping.put("roles_permisos", List.of(
                PermissionRegistry.ROLES_READ.code()
        ));
        return Map.copyOf(mapping);
    }
}
