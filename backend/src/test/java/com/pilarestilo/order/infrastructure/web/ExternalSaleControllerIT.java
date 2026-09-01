package com.pilarestilo.order.infrastructure.web;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class ExternalSaleControllerIT {

    /** Seeded by V61: product 005 has Rojo/S, Rojo/M (stock 1); 010 Marino/38; 012 Negro/38 — all stock 1. */
    private static final String PRODUCT_005 = "10000000-0000-0000-0000-000000000005";
    private static final String PRODUCT_010 = "10000000-0000-0000-0000-000000000010";
    private static final String PRODUCT_012 = "10000000-0000-0000-0000-000000000012";

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
    void requires_orders_create_permission() throws Exception {
        String customerToken = registerAndGetToken("cust_" + UUID.randomUUID() + "@test.com");

        mvc.perform(post("/api/admin/sales/external")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("k-" + UUID.randomUUID(), PRODUCT_005, "Rojo", "S", 1, "15000", "PICKUP", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void registers_a_shipping_sale_and_it_is_readable() throws Exception {
        String admin = loginAdmin();

        MvcResult res = mvc.perform(post("/api/admin/sales/external")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("k-" + UUID.randomUUID(), PRODUCT_005, "Rojo", "S", 1, "15000",
                                "SHIPPING", "Av. Siempre Viva 742")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.deliveryMethod").value("SHIPPING"))
                .andExpect(jsonPath("$.buyerName").value("Javiera"))
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andReturn();

        String id = om.readTree(res.getResponse().getContentAsString()).get("id").asString();
        mvc.perform(get("/api/orders/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void insufficient_stock_is_409() throws Exception {
        // Marino/38 is seeded with stock 1; asking for 5 (within the 999 request cap) goes short.
        mvc.perform(post("/api/admin/sales/external")
                        .header("Authorization", "Bearer " + loginAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("k-" + UUID.randomUUID(), PRODUCT_010, "Marino", "38", 5, "15000", "PICKUP", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void unknown_product_is_404() throws Exception {
        mvc.perform(post("/api/admin/sales/external")
                        .header("Authorization", "Bearer " + loginAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("k-" + UUID.randomUUID(), UUID.randomUUID().toString(), null, null, 1, "1000", "PICKUP", null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void a_repeated_idempotency_key_returns_the_same_order() throws Exception {
        String admin = loginAdmin();
        String key = "k-" + UUID.randomUUID();
        String content = body(key, PRODUCT_012, "Negro", "38", 1, "15000", "PICKUP", null);

        MvcResult first = mvc.perform(post("/api/admin/sales/external")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isCreated()).andReturn();
        MvcResult second = mvc.perform(post("/api/admin/sales/external")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isOk()).andReturn();

        String firstId = om.readTree(first.getResponse().getContentAsString()).get("id").asString();
        String secondId = om.readTree(second.getResponse().getContentAsString()).get("id").asString();
        assertThat(secondId).isEqualTo(firstId);
    }

    // ---- helpers ----

    private String body(String key, String productId, String color, String size, int qty, String price,
                        String delivery, String address) {
        var line = new java.util.LinkedHashMap<String, Object>();
        line.put("productId", productId);
        line.put("variantColor", color);
        line.put("variantSize", size);
        line.put("quantity", qty);
        line.put("unitPrice", price);
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("idempotencyKey", key);
        map.put("buyerName", "Javiera");
        map.put("buyerContact", "+56911112222");
        map.put("salesChannel", "INSTAGRAM");
        map.put("paymentMethod", "TRANSFER");
        map.put("deliveryMethod", delivery);
        map.put("shippingAddress", address);
        map.put("notes", "por IG");
        map.put("items", List.of(line));
        return om.writeValueAsString(map);
    }

    private String loginAdmin() throws Exception {
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

}
