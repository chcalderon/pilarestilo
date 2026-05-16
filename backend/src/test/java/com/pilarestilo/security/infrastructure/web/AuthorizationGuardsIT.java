package com.pilarestilo.security.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class AuthorizationGuardsIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Test
    void unauthenticated_requests_are_denied_on_protected_endpoints() throws Exception {
        mvc.perform(get("/api/orders"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/orders/mine"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/payments"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/payments/order/{orderId}", UUID.randomUUID()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/auth/me/addresses"))
                .andExpect(status().isForbidden());

        String addressBody = om.writeValueAsString(Map.of(
                "label", "Casa",
                "recipientName", "Anon User",
                "phone", "+56912345678",
                "line1", "Sin auth 123",
                "regionId", 13,
                "cityId", 45,
                "comunaId", 273,
                "comuna", "Las Condes",
                "city", "Santiago",
                "region", "Region Metropolitana de Santiago"
        ));
        mvc.perform(post("/api/auth/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void gateway_webhook_endpoint_is_public_but_still_validates_payload() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "paymentId", UUID.randomUUID(),
                "gatewayStatus", "APPROVED"
        ));

        mvc.perform(post("/api/payments/webhooks/gateway")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void mercadopago_webhook_endpoint_is_public() throws Exception {
        mvc.perform(post("/api/payments/webhooks/gateway/mercadopago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void customer_is_forbidden_from_admin_order_and_payment_endpoints() throws Exception {
        String customerToken = registerCustomerAndGetToken("forbidden_customer_" + UUID.randomUUID() + "@test.com");

        String updateStatusBody = om.writeValueAsString(Map.of("status", "PAID"));
        mvc.perform(patch("/api/orders/{id}/status", UUID.randomUUID())
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusBody))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/orders")
                        .header("Authorization", bearer(customerToken)))
                .andExpect(status().isForbidden());

        String reviewBody = om.writeValueAsString(Map.of(
                "action", "APPROVE",
                "reviewerId", UUID.randomUUID()
        ));
        mvc.perform(patch("/api/payments/{id}/review", UUID.randomUUID())
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/payments")
                        .header("Authorization", bearer(customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void customer_can_read_own_order_but_not_order_from_other_customer() throws Exception {
        String customerAToken = registerCustomerAndGetToken("customer_a_" + UUID.randomUUID() + "@test.com");
        String customerBToken = registerCustomerAndGetToken("customer_b_" + UUID.randomUUID() + "@test.com");
        UUID customerAId = getCurrentUserId(customerAToken);

        UUID productId = createProduct(customerAToken, "Auth Guard Product");
        UUID addressId = createAddress(customerAToken);
        UUID orderId = createOrder(customerAToken, customerAId, productId, addressId);

        mvc.perform(get("/api/orders/mine")
                        .header("Authorization", bearer(customerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        mvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", bearer(customerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customerAId.toString()));

        mvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", bearer(customerBToken)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/payments/order/{orderId}", orderId)
                        .header("Authorization", bearer(customerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));

        MvcResult ownPayment = mvc.perform(get("/api/payments/order/{orderId}", orderId)
                        .header("Authorization", bearer(customerAToken)))
                .andExpect(status().isOk())
                .andReturn();
        UUID paymentId = UUID.fromString(om.readTree(ownPayment.getResponse().getContentAsString()).get("id").asText());

        mvc.perform(get("/api/payments/order/{orderId}", orderId)
                        .header("Authorization", bearer(customerBToken)))
                .andExpect(status().isForbidden());

        String proofBody = om.writeValueAsString(Map.of("proofReference", "https://example.com/proof-test.jpg"));
        mvc.perform(patch("/api/payments/{id}/proof", paymentId)
                        .header("Authorization", bearer(customerBToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proofBody))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/payments/{id}/proof", paymentId)
                        .header("Authorization", bearer(customerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proofBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void admin_can_access_restricted_order_and_payment_lists() throws Exception {
        String adminToken = loginAdminAndGetToken();

        mvc.perform(get("/api/orders")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/payments")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    private String registerCustomerAndGetToken(String email) throws Exception {
        String registerBody = om.writeValueAsString(Map.of(
                "email", email,
                "password", "password123",
                "fullName", "Auth Guard Tester"
        ));
        MvcResult register = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn();

        return om.readTree(register.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String loginAdminAndGetToken() throws Exception {
        String adminLoginBody = om.writeValueAsString(Map.of(
                "email", "admin@pilarestilo.com",
                "password", "admin2026"
        ));
        MvcResult adminLogin = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminLoginBody))
                .andExpect(status().isOk())
                .andReturn();

        return om.readTree(adminLogin.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private UUID getCurrentUserId(String token) throws Exception {
        MvcResult me = mvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(om.readTree(me.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID createProduct(String token, String name) throws Exception {
        String productBody = om.writeValueAsString(Map.of(
                "name", name,
                "description", "Product for authorization tests",
                "priceAmount", 120000,
                "priceCurrency", "CLP",
                "imageUrl", "https://example.com/auth-test.jpg",
                "condition", "NEW",
                "brand", "TestBrand",
                "stock", 5,
                "active", true
        ));

        MvcResult created = mvc.perform(post("/api/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(om.readTree(created.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID createOrder(String token, UUID customerId, UUID productId, UUID shippingAddressId) throws Exception {
        String orderBody = om.writeValueAsString(Map.of(
                "customerId", customerId,
                "items", List.of(Map.of("productId", productId, "quantity", 1)),
                "paymentMethod", "TRANSFER",
                "shippingZoneCode", "LOCAL",
                "shippingCourierId", "chilexpress",
                "shippingAddressId", shippingAddressId,
                "notes", "authorization test order"
        ));

        MvcResult created = mvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(om.readTree(created.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID createAddress(String token) throws Exception {
        String body = om.writeValueAsString(Map.of(
                "label", "Casa",
                "recipientName", "Auth Guard Tester",
                "phone", "+56912345678",
                "line1", "Av Apoquindo 123",
                "regionId", 13,
                "cityId", 45,
                "comunaId", 273,
                "comuna", "Las Condes",
                "city", "Santiago",
                "region", "Region Metropolitana de Santiago"
        ));
        MvcResult created = mvc.perform(post("/api/auth/me/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(om.readTree(created.getResponse().getContentAsString()).get("id").asText());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
