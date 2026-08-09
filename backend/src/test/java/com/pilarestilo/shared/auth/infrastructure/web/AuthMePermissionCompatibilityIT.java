package com.pilarestilo.shared.auth.infrastructure.web;

import tools.jackson.databind.ObjectMapper;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.user.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class AuthMePermissionCompatibilityIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("testdb").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @Test
    void auth_me_exposes_dual_permissions_for_new_tokens() throws Exception {
        String token = loginAdmin();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.permissionCodes").isArray())
                .andExpect(jsonPath("$.permissionCodes[?(@ == 'roles.manage')]").exists());
    }

    @Test
    void auth_me_reconstructs_modern_permissions_for_legacy_tokens() throws Exception {
        String legacyToken = jwtTokenProvider.generateAccessToken(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "admin@pilarestilo.com",
                UserRole.ADMIN,
                List.of("dashboard", "configuracion")
        );

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + legacyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0]").value("dashboard"))
                .andExpect(jsonPath("$.permissionCodes[?(@ == 'dashboard.read')]").exists())
                .andExpect(jsonPath("$.permissionCodes[?(@ == 'analytics.read')]").exists())
                .andExpect(jsonPath("$.permissionCodes[?(@ == 'settings.read')]").exists());
    }

    private String loginAdmin() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", "admin@pilarestilo.com", "password", "admin2026"))))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
