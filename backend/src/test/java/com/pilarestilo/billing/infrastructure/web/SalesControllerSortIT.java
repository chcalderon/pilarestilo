package com.pilarestilo.billing.infrastructure.web;

import tools.jackson.databind.JsonNode;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * `GET /api/admin/sales` ignored its own `Pageable`'s sort entirely -- the native query behind it
 * (SalesQueryRepositoryAdapter) hardcoded `ORDER BY o.created_at DESC`, so VentasPage's sortable
 * "Total"/"Fecha" columns had nowhere real to send a sort to. This runs the real endpoint against
 * a real Postgres and reads the response back in order, the same way a false claim of "it sorts"
 * would only be caught by an actual query, not a mock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class SalesControllerSortIT {

    /** Seeded by V61, one unit each -- see ExternalSaleControllerIT for the full seed note. */
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
    void sorts_by_total_amount_ascending_and_descending() throws Exception {
        String admin = loginAdmin();
        registerSale(admin, PRODUCT_005, "Rojo", "S", "10000");
        registerSale(admin, PRODUCT_005, "Rojo", "M", "30000");
        registerSale(admin, PRODUCT_010, "Marino", "38", "20000");

        assertThat(totalsInOrder(admin, "totalAmount,asc"))
                .isSortedAccordingTo(Comparator.naturalOrder());
        assertThat(totalsInOrder(admin, "totalAmount,desc"))
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    /** An unrecognized sort property must fall through to the existing default rather than
     * reaching the query -- it never gets the chance to mean anything else. */
    @Test
    void an_unrecognized_sort_property_does_not_break_the_query() throws Exception {
        String admin = loginAdmin();
        registerSale(admin, PRODUCT_012, "Negro", "38", "15000");

        mvc.perform(get("/api/admin/sales?sort=customerName,asc&page=0&size=20")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    // ---- helpers ----

    private List<BigDecimal> totalsInOrder(String admin, String sort) throws Exception {
        MvcResult res = mvc.perform(get("/api/admin/sales?sort=" + sort + "&page=0&size=20")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = om.readTree(res.getResponse().getContentAsString()).get("content");
        List<BigDecimal> totals = new ArrayList<>();
        for (JsonNode row : content) {
            totals.add(new BigDecimal(row.get("totalAmount").asString()));
        }
        return totals;
    }

    private void registerSale(String admin, String productId, String color, String size, String price) throws Exception {
        var line = new java.util.LinkedHashMap<String, Object>();
        line.put("productId", productId);
        line.put("variantColor", color);
        line.put("variantSize", size);
        line.put("quantity", 1);
        line.put("unitPrice", price);
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("idempotencyKey", "k-" + java.util.UUID.randomUUID());
        body.put("buyerName", "Comprador Sort");
        body.put("buyerContact", "+56911112222");
        body.put("salesChannel", "INSTAGRAM");
        body.put("paymentMethod", "TRANSFER");
        body.put("deliveryMethod", "PICKUP");
        body.put("notes", "sort test");
        body.put("items", List.of(line));

        mvc.perform(post("/api/admin/sales/external")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private String loginAdmin() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", "admin@pilarestilo.com", "password", "admin2026"))))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asString();
    }
}
