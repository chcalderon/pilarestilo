package com.pilarestilo.rbac.application;

import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.application.usecases.LoginUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class LoginWithPermissionsTest {

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
    LoginUseCase loginUseCase;

    @Test
    void admin_login_returns_permissions_in_token_dto() {
        AuthTokenDto dto = loginUseCase.execute("admin@pilarestilo.com", "admin2026");
        assertNotNull(dto.permissions());
        assertNotNull(dto.permissionCodes());
        assertTrue(dto.permissions().contains("dashboard"));
        assertTrue(dto.permissions().contains("configuracion"));
        assertTrue(dto.permissionCodes().contains("dashboard.read"));
        assertTrue(dto.permissionCodes().contains("roles.manage"));
    }
}
