package com.pilarestilo.rbac.domain;

import com.pilarestilo.shared.rbac.domain.model.RolePermission;
import com.pilarestilo.user.domain.enums.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RolePermissionTest {

    @Test
    void creates_with_role_and_view_key() {
        RolePermission rp = new RolePermission(UserRole.SELLER, "dashboard");
        assertEquals(UserRole.SELLER, rp.getRole());
        assertEquals("dashboard", rp.getViewKey());
    }

    @Test
    void equal_instances_have_same_role_and_view_key() {
        RolePermission a = new RolePermission(UserRole.SELLER, "caja");
        RolePermission b = new RolePermission(UserRole.SELLER, "caja");
        assertEquals(a, b);
    }
}
