package com.pilarestilo.rbac.application;

import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.shared.rbac.application.RbacAuthorizationEvaluator;
import com.pilarestilo.shared.rbac.domain.PermissionRegistry;
import com.pilarestilo.user.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacAuthorizationEvaluatorTest {

    private final RbacAuthorizationEvaluator evaluator = new RbacAuthorizationEvaluator();

    @Test
    void evaluator_checks_permission_definitions_without_inline_strings() {
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(),
                "seller@pilarestilo.com",
                UserRole.SELLER,
                List.of("dashboard", "productos"),
                List.of("products.read", "cash.read", "pos.sale.create")
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.toAuthorities());

        assertTrue(evaluator.hasPermission(authentication, PermissionRegistry.PRODUCTS_READ));
        assertTrue(evaluator.hasAnyPermission(authentication, PermissionRegistry.CASH_READ, PermissionRegistry.CASH_CLOSE));
        assertFalse(evaluator.hasAllPermissions(authentication, PermissionRegistry.PRODUCTS_READ, PermissionRegistry.CASH_CLOSE));
        assertFalse(evaluator.hasPermission(authentication, PermissionRegistry.ROLES_MANAGE));
    }
}
