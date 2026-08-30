package com.pilarestilo.cashregister.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke-tests for the POS stub endpoint.
 *
 * Verifies that:
 * - Unauthenticated callers get 401
 * - Authenticated SELLER/ADMIN callers get 501 (stub, not yet implemented)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class PosControllerTest {

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

    @Autowired
    MockMvc mvc;

    @Test
    void unauthenticated_call_is_rejected() throws Exception {
        // Spring Security returns 403 (not 401) for missing credentials — this is the
        // project-wide default; see customer_cannot_open_caja in CajaIT for precedent.
        mvc.perform(post("/api/pos/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticated_seller_gets_501_not_implemented() throws Exception {
        String token = loginAdmin();

        mvc.perform(post("/api/pos/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotImplemented());
    }

    private String loginAdmin() throws Exception {
        var result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@pilarestilo.com\",\"password\":\"admin2026\"}"))
                .andExpect(status().isOk())
                .andReturn();
        var body = result.getResponse().getContentAsString();
        // extract accessToken from JSON manually to avoid ObjectMapper dep
        int start = body.indexOf("\"accessToken\":\"") + 15;
        int end = body.indexOf("\"", start);
        return body.substring(start, end);
    }
}
