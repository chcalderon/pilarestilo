package com.pilarestilo.rbac.domain;

import com.pilarestilo.shared.rbac.domain.PermissionRegistry;
import com.pilarestilo.shared.rbac.domain.model.PermissionDefinition;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PermissionRegistryTest {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z]+(?:\\.[a-z_]+)+$");

    @Test
    void registry_codes_are_unique_and_namespaced() {
        Set<String> codes = PermissionRegistry.all().stream()
                .map(PermissionDefinition::code)
                .collect(Collectors.toSet());

        assertEquals(PermissionRegistry.all().size(), codes.size());
        assertTrue(codes.contains("products.read"));
        assertTrue(codes.contains("cash.close"));
        assertTrue(codes.contains("pos.sale.create"));
        assertTrue(codes.stream().allMatch(code -> CODE_PATTERN.matcher(code).matches()));
    }

    @Test
    void registry_lookup_returns_known_definition() {
        PermissionDefinition definition = PermissionRegistry.require("dispatch.manage");

        assertEquals("dispatch.manage", definition.code());
        assertEquals("dispatch", definition.module().code());
        assertEquals("workflow", definition.category().code());
        assertEquals("PERM_dispatch.manage", definition.authority());
    }
}
