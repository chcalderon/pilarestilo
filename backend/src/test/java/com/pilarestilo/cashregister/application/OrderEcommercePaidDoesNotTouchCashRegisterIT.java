package com.pilarestilo.cashregister.application;

import tools.jackson.databind.ObjectMapper;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import com.pilarestilo.order.domain.model.OrderReference;
import com.pilarestilo.order.infrastructure.persistence.entities.OrderEntity;
import com.pilarestilo.order.infrastructure.persistence.repositories.OrderJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that an ECOMMERCE order being paid does NOT create a CashMovement.
 *
 * TDD Red: FAILS while OrderPaidCashRegisterListener exists (it fires on any PAID event
 * regardless of channel and writes a SALE movement).
 *
 * TDD Green: passes after the listener is deleted (Fase 2 Step 2).
 *
 * Setup strategy:
 *  - Register + promote a seller via API so users.id FK for cash_registers is satisfied.
 *  - Insert a bare order row directly via OrderJpaRepository (satisfies cash_movements.order_id FK).
 *  - @MockitoBean DispatchRepository neutralises OrderPaidDispatchListener (no real dispatch insert).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class OrderEcommercePaidDoesNotTouchCashRegisterIT {

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
    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired CashRegisterRepository cashRegisterRepository;
    /** Used to insert a bare order row so cash_movements.order_id FK resolves on RED. */
    @Autowired OrderJpaRepository orderJpaRepository;

    /** Neutralises OrderPaidDispatchListener to avoid dispatches.order_id FK failure. */
    @MockitoBean DispatchRepository dispatchRepository;

    @Test
    void ecommerce_order_paid_does_not_create_cash_movement() throws Exception {
        // ── Step 1: create a real seller user and open a cash register via API ──────────
        String adminToken = loginAdmin();
        String sellerEmail = "seller_ecom_" + UUID.randomUUID() + "@test.com";
        String sellerIdStr = registerAndGetId(sellerEmail);
        promoteToSeller(adminToken, sellerIdStr);
        String sellerToken = loginUser(sellerEmail, "pass1234");

        mvc.perform(post("/api/caja/open")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("openingBalance", 50_000))))
                .andExpect(status().isCreated());

        MvcResult currentResult = mvc.perform(get("/api/caja/current")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andReturn();
        UUID cashRegisterId = UUID.fromString(
                om.readTree(currentResult.getResponse().getContentAsString()).get("id").asString());

        int movementsBefore = cashRegisterRepository.findById(cashRegisterId)
                .orElseThrow().getMovements().size();

        // ── Step 2: set up IDs, insert bare order row, and stub dependencies ─────────
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.fromString(sellerIdStr);

        // Insert a minimal order row so cash_movements.order_id FK is satisfied when the
        // listener (RED phase) tries to write a movement. Uses seller as customerId
        // so orders.customer_id → users.id FK is also satisfied.
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setId(orderId);
        // Built entity-first to satisfy the cash_movements FK, so it bypasses Order.create(),
        // which is what normally mints the reference.
        orderEntity.setPublicReference(OrderReference.forOrderId(orderId));
        orderEntity.setCustomerId(customerId);
        orderEntity.setSubtotalAmount(BigDecimal.valueOf(10_000));
        orderEntity.setSubtotalCurrency("CLP");
        orderEntity.setDiscountAmount(BigDecimal.ZERO);
        orderEntity.setDiscountCurrency("CLP");
        orderEntity.setTotalAmount(BigDecimal.valueOf(10_000));
        orderEntity.setTotalCurrency("CLP");
        // NOT NULL since V79, and the row has to reconcile: chk_orders_tax_reconciles asserts
        // net + tax = total. Built by hand rather than through TaxBreakdown because this is a bare
        // row for an FK, not a sale — but the numbers still have to be a real split of 10.000 at 19%.
        orderEntity.setNetAmount(BigDecimal.valueOf(8_403));
        orderEntity.setTaxAmount(BigDecimal.valueOf(1_597));
        orderEntity.setTaxRate(new BigDecimal("19.00"));
        orderEntity.setPaymentMethod(PaymentMethod.TRANSFER);
        orderEntity.setShippingZoneCode("RM");
        orderEntity.setShippingCourierId("chilexpress");
        orderEntity.setShippingCourierName("Chilexpress");
        orderEntity.setShippingPaymentMode("PREPAID");
        orderEntity.setShippingAddressReference("Ref test");
        orderEntity.setSalesChannel(SalesChannel.ECOMMERCE);
        orderEntity.setStatus(OrderStatus.PAID);
        orderEntity.setCreatedAt(Instant.now());
        orderEntity.setUpdatedAt(Instant.now());
        orderJpaRepository.save(orderEntity);

        // Stub DispatchRepository so OrderPaidDispatchListener doesn't hit the DB
        when(dispatchRepository.existsByOrderId(any())).thenReturn(false);
        when(dispatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // ── Act: publish PAID event for the ECOMMERCE order ──────────────────────────
        eventPublisher.publishEvent(new OrderStatusChanged(
                orderId, customerId,
                OrderStatus.PENDING_PAYMENT, OrderStatus.PAID,
                Instant.now()
        ));

        // ── Assert: NO new CashMovement must have been created ────────────────────────
        int movementsAfter = cashRegisterRepository.findById(cashRegisterId)
                .orElseThrow().getMovements().size();

        assertThat(movementsAfter)
                .as("ECOMMERCE paid order must NOT create a CashMovement")
                .isEqualTo(movementsBefore);
    }

    // ── helpers (same pattern as CajaIT) ─────────────────────────────────────

    private String loginAdmin() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "email", "admin@pilarestilo.com", "password", "admin2026"))))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private String registerAndGetId(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "email", email, "password", "pass1234", "fullName", "Test"))))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("userId").asString();
    }

    private void promoteToSeller(String adminToken, String userId) throws Exception {
        mvc.perform(post("/api/admin/workers/" + userId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "role", "SELLER", "vigencyStart", "2020-01-01"))))
                .andExpect(status().isOk());
    }

    private String loginUser(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asString();
    }
}
