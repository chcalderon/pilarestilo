package com.pilarestilo.dashboard.infrastructure.web;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DashboardControllerIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("pilarestilo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper om;

    private String loginAdmin() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", "admin@pilarestilo.com", "password", "admin2026"))))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String registerAndGetId(String email) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", "pass1234", "fullName", "Test User"))))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("userId").asText();
    }

    private String promoteAndLogin(String adminToken, String userId, String role, String email) throws Exception {
        mockMvc.perform(post("/api/admin/workers/" + userId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("role", role, "vigencyStart", "2020-01-01"))))
                .andExpect(status().isOk());
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", "pass1234"))))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void adminGetsAdminStats() throws Exception {
        String token = loginAdmin();
        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.dailySales").exists())
                .andExpect(jsonPath("$.dailyRevenueSeries").isArray());
    }

    @Test
    void sellerGetsSellerStats() throws Exception {
        String adminToken = loginAdmin();
        String email = "seller_dash_" + System.currentTimeMillis() + "@test.com";
        String userId = registerAndGetId(email);
        String token = promoteAndLogin(adminToken, userId, "SELLER", email);

        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SELLER"));
    }

    @Test
    void despachadorGetsDespachadorStats() throws Exception {
        String adminToken = loginAdmin();
        String email = "desp_dash_" + System.currentTimeMillis() + "@test.com";
        String userId = registerAndGetId(email);
        String token = promoteAndLogin(adminToken, userId, "DESPACHADOR", email);

        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DESPACHADOR"))
                .andExpect(jsonPath("$.pendingDispatches").isNumber());
    }

    @Test
    void administracionGetsAdministracionStats() throws Exception {
        String adminToken = loginAdmin();
        String email = "adm_dash_" + System.currentTimeMillis() + "@test.com";
        String userId = registerAndGetId(email);
        String token = promoteAndLogin(adminToken, userId, "ADMINISTRACION", email);

        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMINISTRACION"))
                .andExpect(jsonPath("$.activeWorkers").isNumber());
    }

    @Test
    void unauthenticatedReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isForbidden());
    }
}
