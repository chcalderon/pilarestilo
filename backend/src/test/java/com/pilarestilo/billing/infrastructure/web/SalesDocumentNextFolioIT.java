package com.pilarestilo.billing.infrastructure.web;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The shop still types every folio in by hand from the SII's eBoleta app -- see
 * SuggestNextFolioUseCase's own note -- but retyping it fresh each time is where a transposed
 * digit lives. This proves the suggestion against a real Postgres: one past the highest folio
 * actually stored for that document type, boleta and factura kept apart.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class SalesDocumentNextFolioIT {

    private static final String PRODUCT_005 = "10000000-0000-0000-0000-000000000005";
    private static final String PRODUCT_010 = "10000000-0000-0000-0000-000000000010";

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
    void suggests_one_past_the_highest_boleta_folio_stored() throws Exception {
        String admin = loginAdmin();
        String orderId = registerSale(admin, PRODUCT_005, "Rojo", "S");
        issueBoleta(admin, orderId, "2099");

        mvc.perform(get("/api/admin/sales-documents/next-folio?documentType=BOLETA")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextFolio").value(2100));
    }

    /** Boleta and factura draw from separate SII folio ranges; issuing a boleta must never move
     * factura's own suggestion. */
    @Test
    void keeps_boleta_and_factura_folio_sequences_apart() throws Exception {
        String admin = loginAdmin();
        String orderId = registerSale(admin, PRODUCT_010, "Marino", "38");
        issueBoleta(admin, orderId, "500");

        mvc.perform(get("/api/admin/sales-documents/next-folio?documentType=FACTURA")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextFolio").doesNotExist());
    }

    // ---- helpers ----

    private void issueBoleta(String admin, String orderId, String folio) throws Exception {
        var body = Map.of("orderId", orderId, "documentType", "BOLETA", "folio", folio);
        mvc.perform(post("/api/admin/sales-documents")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private String registerSale(String admin, String productId, String color, String size) throws Exception {
        var line = new java.util.LinkedHashMap<String, Object>();
        line.put("productId", productId);
        line.put("variantColor", color);
        line.put("variantSize", size);
        line.put("quantity", 1);
        line.put("unitPrice", "15000");
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("idempotencyKey", "k-" + UUID.randomUUID());
        body.put("buyerName", "Comprador Folio");
        body.put("buyerContact", "+56911112222");
        body.put("salesChannel", "INSTAGRAM");
        body.put("paymentMethod", "TRANSFER");
        body.put("deliveryMethod", "PICKUP");
        body.put("notes", "next-folio test");
        body.put("items", List.of(line));

        MvcResult res = mvc.perform(post("/api/admin/sales/external")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asString();
    }

    private String loginAdmin() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", "admin@pilarestilo.com", "password", "admin2026"))))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asString();
    }
}
