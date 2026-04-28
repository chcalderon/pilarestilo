package com.pilarestilo.rbac.infrastructure;

import com.pilarestilo.shared.rbac.domain.model.RolePermission;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionRepository;
import com.pilarestilo.user.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class RolePermissionRepositoryAdapterTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    RolePermissionRepository repo;

    @Test
    void seller_has_expected_view_keys_from_seed() {
        List<String> keys = repo.findViewKeysByRole(UserRole.SELLER);
        assertTrue(keys.contains("dashboard"));
        assertTrue(keys.contains("caja"));
        assertTrue(keys.contains("productos"));
    }

    @Test
    void customer_has_no_permissions() {
        List<String> keys = repo.findViewKeysByRole(UserRole.CUSTOMER);
        assertTrue(keys.isEmpty());
    }

    @Test
    void admin_has_all_permissions() {
        List<String> keys = repo.findViewKeysByRole(UserRole.ADMIN);
        assertTrue(keys.contains("configuracion"));
        assertTrue(keys.contains("roles_permisos"));
        assertEquals(7, keys.size());
    }
}
