package com.pilarestilo.rbac.infrastructure;

import com.pilarestilo.support.NotificationsTestDatabase;
import com.pilarestilo.shared.rbac.domain.PermissionRegistry;
import com.pilarestilo.shared.rbac.infrastructure.persistence.repositories.PermissionJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
class PermissionCatalogSeedIT {

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
    PermissionJpaRepository permissionJpaRepository;

    @Test
    void database_catalog_matches_registry_codes() {
        Set<String> expected = PermissionRegistry.all().stream()
                .map(definition -> definition.code())
                .collect(Collectors.toSet());
        Set<String> actual = permissionJpaRepository.findAll().stream()
                .map(entity -> entity.getCode())
                .collect(Collectors.toSet());

        assertEquals(expected, actual);
    }
}
