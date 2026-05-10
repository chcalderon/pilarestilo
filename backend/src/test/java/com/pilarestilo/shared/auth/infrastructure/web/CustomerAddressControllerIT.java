package com.pilarestilo.shared.auth.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class CustomerAddressControllerIT {

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
    void customer_can_manage_addresses_and_default_flag() throws Exception {
        String token = registerCustomerAndGetToken("addr_crud_" + UUID.randomUUID() + "@test.com");

        MvcResult createdOne = mvc.perform(post("/api/auth/me/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "label", "Casa",
                                "recipientName", "Pilar Estilo",
                                "phone", "+56912345678",
                                "line1", "Av Apoquindo 123",
                                "comuna", "Las Condes",
                                "city", "Santiago",
                                "region", "Metropolitana"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true))
                .andReturn();
        String firstAddressId = om.readTree(createdOne.getResponse().getContentAsString()).get("id").asText();

        MvcResult createdTwo = mvc.perform(post("/api/auth/me/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "label", "Oficina",
                                "recipientName", "Pilar Estilo",
                                "phone", "+56987654321",
                                "line1", "Nueva Costanera 100",
                                "line2", "Piso 4",
                                "comuna", "Vitacura",
                                "city", "Santiago",
                                "region", "Metropolitana",
                                "reference", "Recepcion principal"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(false))
                .andReturn();
        String secondAddressId = om.readTree(createdTwo.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(get("/api/auth/me/addresses")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].updatedAt").exists());

        mvc.perform(patch("/api/auth/me/addresses/{id}", secondAddressId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "label", "Oficina Centro",
                                "recipientName", "Pilar E.",
                                "phone", "+56999888777",
                                "line1", "Av Vitacura 200",
                                "line2", "Of 12",
                                "comuna", "Vitacura",
                                "city", "Santiago",
                                "region", "Metropolitana",
                                "reference", "Conserjeria"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Oficina Centro"))
                .andExpect(jsonPath("$.phone").value("+56999888777"));

        mvc.perform(patch("/api/auth/me/addresses/{id}/default", secondAddressId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));

        MvcResult listAfterDefault = mvc.perform(get("/api/auth/me/addresses")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + secondAddressId + "')]").value(hasSize(1)))
                .andReturn();
        JsonNode afterDefaultRows = om.readTree(listAfterDefault.getResponse().getContentAsString());
        boolean firstAddressStillDefault = false;
        for (JsonNode row : afterDefaultRows) {
            if (firstAddressId.equals(row.path("id").asText())) {
                firstAddressStillDefault = row.path("isDefault").asBoolean(false);
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertFalse(firstAddressStillDefault);

        mvc.perform(delete("/api/auth/me/addresses/{id}", firstAddressId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me/addresses")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(secondAddressId));
    }

    @Test
    void customer_cannot_modify_other_customer_address() throws Exception {
        String tokenA = registerCustomerAndGetToken("addr_owner_a_" + UUID.randomUUID() + "@test.com");
        String tokenB = registerCustomerAndGetToken("addr_owner_b_" + UUID.randomUUID() + "@test.com");

        MvcResult created = mvc.perform(post("/api/auth/me/addresses")
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "label", "Casa",
                                "recipientName", "Pilar A",
                                "phone", "+56911112222",
                                "line1", "Linea A",
                                "comuna", "Comuna A",
                                "city", "Santiago",
                                "region", "Metropolitana"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String addressId = om.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(patch("/api/auth/me/addresses/{id}", addressId)
                        .header("Authorization", bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "label", "Hack",
                                "recipientName", "Hack",
                                "phone", "+56999999999",
                                "line1", "Hack line",
                                "comuna", "Hack comuna",
                                "city", "Hack city",
                                "region", "Hack region"
                        ))))
                .andExpect(status().isNotFound());
    }

    @Test
    void order_creation_requires_shipping_address_id_and_ownership() throws Exception {
        String customerToken = registerCustomerAndGetToken("addr_order_a_" + UUID.randomUUID() + "@test.com");
        String otherToken = registerCustomerAndGetToken("addr_order_b_" + UUID.randomUUID() + "@test.com");
        UUID customerId = getCurrentUserId(customerToken);
        UUID productId = createProduct(customerToken, "Address gated checkout product");
        ShippingSelection shipping = resolveShippingSelection();

        mvc.perform(post("/api/orders")
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "customerId", customerId,
                                "items", List.of(Map.of("productId", productId, "quantity", 1)),
                                "paymentMethod", "BANK_TRANSFER",
                                "shippingZoneCode", shipping.zoneCode(),
                                "shippingCourierId", shipping.courierId()
                        ))))
                .andExpect(status().isUnprocessableEntity());

        String otherAddressId = createAddressAndReturnId(otherToken, "Casa B");
        mvc.perform(post("/api/orders")
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "customerId", customerId,
                                "items", List.of(Map.of("productId", productId, "quantity", 1)),
                                "paymentMethod", "BANK_TRANSFER",
                                "shippingZoneCode", shipping.zoneCode(),
                                "shippingCourierId", shipping.courierId(),
                                "shippingAddressId", otherAddressId
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Shipping address not found")));
    }

    @Test
    void order_persists_shipping_address_snapshot_and_address_id() throws Exception {
        String customerToken = registerCustomerAndGetToken("addr_snapshot_" + UUID.randomUUID() + "@test.com");
        UUID customerId = getCurrentUserId(customerToken);
        UUID productId = createProduct(customerToken, "Snapshot product");
        ShippingSelection shipping = resolveShippingSelection();
        String addressId = createAddressAndReturnId(customerToken, "Casa Snapshot");

        MvcResult createdOrder = mvc.perform(post("/api/orders")
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "customerId", customerId,
                                "items", List.of(Map.of("productId", productId, "quantity", 1)),
                                "paymentMethod", "BANK_TRANSFER",
                                "shippingZoneCode", shipping.zoneCode(),
                                "shippingCourierId", shipping.courierId(),
                                "shippingAddressId", addressId
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shippingAddressId").value(addressId))
                .andExpect(jsonPath("$.shippingAddressReference", containsString("Av Apoquindo 123")))
                .andReturn();
        String orderId = om.readTree(createdOrder.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(patch("/api/auth/me/addresses/{id}", addressId)
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "label", "Casa Snapshot",
                                "recipientName", "Cliente Snapshot",
                                "phone", "+56911113333",
                                "line1", "Otra direccion 999",
                                "line2", "Depto B",
                                "comuna", "Providencia",
                                "city", "Santiago",
                                "region", "Metropolitana",
                                "reference", "Porteria"
                        ))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", bearer(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingAddressId").value(addressId))
                .andExpect(jsonPath("$.shippingAddressReference", containsString("Av Apoquindo 123")))
                .andExpect(jsonPath("$.shippingAddressReference", not(containsString("Otra direccion 999"))));
    }

    private String createAddressAndReturnId(String token, String label) throws Exception {
        MvcResult created = mvc.perform(post("/api/auth/me/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "label", label,
                                "recipientName", "Cliente Direccion",
                                "phone", "+56912345678",
                                "line1", "Av Apoquindo 123",
                                "line2", "Depto 5",
                                "comuna", "Las Condes",
                                "city", "Santiago",
                                "region", "Metropolitana",
                                "reference", "Conserjeria"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return om.readTree(created.getResponse().getContentAsString()).get("id").asText();
    }

    private ShippingSelection resolveShippingSelection() throws Exception {
        MvcResult settings = mvc.perform(get("/api/system-settings/public"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = om.readTree(settings.getResponse().getContentAsString());
        JsonNode zones = safeArray(root.path("shippingZonesJson").asText(null));
        JsonNode couriers = safeArray(root.path("shippingCouriersJson").asText(null));
        String zoneCode = "LOCAL";
        String courierId = "chilexpress";

        for (JsonNode zone : zones) {
            if (zone.path("active").asBoolean(true)) {
                String candidate = zone.path("code").asText("").trim();
                if (!candidate.isBlank()) {
                    zoneCode = candidate;
                    break;
                }
            }
        }
        for (JsonNode courier : couriers) {
            if (courier.path("active").asBoolean(true)) {
                String candidate = courier.path("id").asText("").trim();
                if (!candidate.isBlank()) {
                    courierId = candidate;
                    break;
                }
            }
        }
        return new ShippingSelection(zoneCode, courierId);
    }

    private JsonNode safeArray(String rawJson) throws Exception {
        if (rawJson == null || rawJson.isBlank()) {
            return om.createArrayNode();
        }
        JsonNode parsed = om.readTree(rawJson);
        return parsed != null && parsed.isArray() ? parsed : om.createArrayNode();
    }

    private String registerCustomerAndGetToken(String email) throws Exception {
        String registerBody = om.writeValueAsString(Map.of(
                "email", email,
                "password", "password123",
                "fullName", "Address Tester"
        ));
        MvcResult register = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn();

        return om.readTree(register.getResponse().getContentAsString()).get("accessToken").asText();
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
                "description", "Product for address tests",
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

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record ShippingSelection(String zoneCode, String courierId) {
    }
}
