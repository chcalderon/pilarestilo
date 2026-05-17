package com.pilarestilo.rbac.application;

import com.pilarestilo.shared.rbac.application.LegacyViewPermissionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyViewPermissionMapperTest {

    private final LegacyViewPermissionMapper mapper = new LegacyViewPermissionMapper();

    @Test
    void productos_maps_to_expected_modern_permissions() {
        List<String> codes = mapper.toPermissionCodes(List.of("productos"));

        assertEquals(List.of(
                "products.read",
                "categories.read",
                "navigation.read",
                "reviews.read",
                "discounts.read",
                "publications.read"
        ), codes);
    }

    @Test
    void mapper_deduplicates_and_ignores_unknown_keys() {
        List<String> codes = mapper.toPermissionCodes(List.of("dashboard", "dashboard", "unknown", "configuracion"));

        assertEquals(List.of("dashboard.read", "analytics.read", "settings.read"), codes);
        assertTrue(codes.stream().noneMatch("unknown"::equals));
    }
}
