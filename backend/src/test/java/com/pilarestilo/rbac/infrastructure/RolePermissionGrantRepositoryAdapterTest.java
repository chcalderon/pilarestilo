package com.pilarestilo.rbac.infrastructure;

import com.pilarestilo.support.NotificationsTestDatabase;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionGrantRepository;
import com.pilarestilo.user.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class RolePermissionGrantRepositoryAdapterTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("testdb").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        NotificationsTestDatabase.register(r, postgres);
    }

    @Autowired
    RolePermissionGrantRepository repo;

    @Test
    void seller_has_expected_seeded_modern_grants() {
        List<String> codes = repo.findPermissionCodesByRole(UserRole.SELLER);

        assertTrue(codes.contains("products.read"));
        assertTrue(codes.contains("cash.read"));
        assertTrue(codes.contains("pos.sale.create"));
    }
}
