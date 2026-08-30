package com.pilarestilo.rbac.infrastructure.web;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class WorkerPermissionsIT {

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

    @Test
    void admin_can_list_workers() throws Exception {
        String token = loginAdmin();
        mvc.perform(get("/api/admin/workers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void admin_can_assign_worker_role() throws Exception {
        String adminToken = loginAdmin();
        String userId = registerAndGetId("worker_" + System.currentTimeMillis() + "@test.com");

        String body = om.writeValueAsString(Map.of(
                "role", "SELLER",
                "vigencyStart", "2026-05-01"
        ));
        mvc.perform(post("/api/admin/workers/" + userId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SELLER"));
    }

    @Test
    void non_admin_cannot_assign_workers() throws Exception {
        String customerToken = registerAndGetToken("cust_" + System.currentTimeMillis() + "@test.com");
        String targetId = registerAndGetId("target_" + System.currentTimeMillis() + "@test.com");

        String body = om.writeValueAsString(Map.of("role", "SELLER", "vigencyStart", "2026-05-01"));
        mvc.perform(post("/api/admin/workers/" + targetId + "/assign")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_get_permission_matrix() throws Exception {
        String token = loginAdmin();
        mvc.perform(get("/api/admin/permissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isArray());
    }

    private String loginAdmin() throws Exception {
        // admin@pilarestilo.com / admin2026 — see V6__seed_v2.sql
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", "admin@pilarestilo.com", "password", "admin2026"))))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", "pass1234", "fullName", "Test"))))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private String registerAndGetId(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", "pass1234", "fullName", "Test"))))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("userId").asString();
    }
}
