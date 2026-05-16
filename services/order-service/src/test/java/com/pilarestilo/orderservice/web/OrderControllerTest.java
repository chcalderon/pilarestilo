package com.pilarestilo.orderservice.web;

import com.pilarestilo.orderservice.application.OrderCommandService;
import com.pilarestilo.orderservice.application.OrderQueryService;
import com.pilarestilo.orderservice.auth.AuthenticatedUser;
import com.pilarestilo.orderservice.auth.UserRole;
import com.pilarestilo.orderservice.persistence.OrderEntity;
import com.pilarestilo.orderservice.web.dto.OrderDto;
import com.pilarestilo.orderservice.web.dto.UpdateOrderStatusRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private static final String INTERNAL_TOKEN = "order-service-internal-token";

    @Mock
    private OrderQueryService queryService;
    @Mock
    private OrderCommandService commandService;
    @Mock
    private HttpServletRequest request;

    private OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(queryService, commandService, INTERNAL_TOKEN);
    }

    @Test
    void updateStatus_allows_admin_role() {
        UUID orderId = UUID.randomUUID();
        when(commandService.updateStatus(eq(orderId), eq("SHIPPED")))
                .thenReturn(sampleOrder(orderId, "SHIPPED"));

        OrderDto result = controller.updateStatus(
                orderId,
                new UpdateOrderStatusRequest("SHIPPED"),
                new AuthenticatedUser(UUID.randomUUID(), "admin@pilarestilo.com", UserRole.ADMIN, false),
                request
        );

        assertEquals("SHIPPED", result.status());
    }

    @Test
    void updateStatus_allows_internal_header_without_authenticated_user() {
        UUID orderId = UUID.randomUUID();
        when(request.getHeader("X-Service-Token")).thenReturn(INTERNAL_TOKEN);
        when(commandService.updateStatus(eq(orderId), eq("DELIVERED")))
                .thenReturn(sampleOrder(orderId, "DELIVERED"));

        OrderDto result = controller.updateStatus(
                orderId,
                new UpdateOrderStatusRequest("DELIVERED"),
                null,
                request
        );

        assertEquals("DELIVERED", result.status());
    }

    @Test
    void updateStatus_rejects_customer_without_internal_token() {
        UUID orderId = UUID.randomUUID();
        when(request.getHeader("X-Service-Token")).thenReturn(null);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> controller.updateStatus(
                orderId,
                new UpdateOrderStatusRequest("SHIPPED"),
                new AuthenticatedUser(UUID.randomUUID(), "customer@pilarestilo.com", UserRole.CUSTOMER, false),
                request
        ));

        assertEquals("Forbidden", ex.getMessage());
    }

    @Test
    void updateStatus_maps_not_found_to_404() {
        UUID orderId = UUID.randomUUID();
        when(commandService.updateStatus(eq(orderId), eq("SHIPPED")))
                .thenThrow(new NoSuchElementException("Order not found: " + orderId));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.updateStatus(
                orderId,
                new UpdateOrderStatusRequest("SHIPPED"),
                new AuthenticatedUser(UUID.randomUUID(), "admin@pilarestilo.com", UserRole.ADMIN, false),
                request
        ));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateStatus_maps_bad_request_to_400() {
        UUID orderId = UUID.randomUUID();
        when(commandService.updateStatus(eq(orderId), eq("CREATED")))
                .thenThrow(new IllegalArgumentException("Unsupported target status: CREATED"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.updateStatus(
                orderId,
                new UpdateOrderStatusRequest("CREATED"),
                new AuthenticatedUser(UUID.randomUUID(), "admin@pilarestilo.com", UserRole.ADMIN, false),
                request
        ));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    private OrderEntity sampleOrder(UUID orderId, String status) {
        OrderEntity entity = new OrderEntity();
        entity.setId(orderId);
        entity.setCustomerId(UUID.randomUUID());
        entity.setSubtotalAmount(new BigDecimal("100.00"));
        entity.setSubtotalCurrency("CLP");
        entity.setDiscountAmount(new BigDecimal("0.00"));
        entity.setDiscountCurrency("CLP");
        entity.setTotalAmount(new BigDecimal("100.00"));
        entity.setTotalCurrency("CLP");
        entity.setPaymentMethod("TRANSFER");
        entity.setShippingZoneCode("LOCAL");
        entity.setShippingCourierId("starken");
        entity.setShippingCourierName("Starken");
        entity.setShippingPaymentMode("POR_PAGAR");
        entity.setShippingAddressId(UUID.randomUUID());
        entity.setShippingAddressReference("Cliente | +56912345678 | Av Apoquindo 123, Las Condes, Santiago");
        entity.setStatus(status);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }
}
