package com.pilarestilo.paymentservice.web;

import com.pilarestilo.paymentservice.application.PaymentQueryService;
import com.pilarestilo.paymentservice.auth.AuthenticatedUser;
import com.pilarestilo.paymentservice.auth.UserRole;
import com.pilarestilo.paymentservice.persistence.OrderEntity;
import com.pilarestilo.paymentservice.persistence.PaymentEntity;
import com.pilarestilo.paymentservice.web.dto.PaymentDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentQueryService queryService;

    @Test
    void list_maps_results() {
        PaymentController controller = new PaymentController(queryService);
        PageRequest pageRequest = PageRequest.of(0, 10);
        PaymentEntity payment = payment(UUID.randomUUID(), UUID.randomUUID());
        when(queryService.list("PENDING", pageRequest)).thenReturn(new PageImpl<>(List.of(payment)));

        Page<PaymentDto> result = controller.list("PENDING", pageRequest);

        assertEquals(1, result.getTotalElements());
        assertEquals(payment.getId(), result.getContent().get(0).id());
    }

    @Test
    void get_by_id_maps_not_found_to_404() {
        PaymentController controller = new PaymentController(queryService);
        UUID paymentId = UUID.randomUUID();
        when(queryService.getById(paymentId)).thenThrow(new NoSuchElementException("Payment not found: " + paymentId));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.getById(paymentId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void get_by_order_id_blocks_foreign_customer() {
        PaymentController controller = new PaymentController(queryService);
        UUID orderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherCustomer = UUID.randomUUID();
        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setCustomerId(ownerId);
        when(queryService.getOrderById(orderId)).thenReturn(order);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> controller.getByOrderId(
                orderId,
                new AuthenticatedUser(otherCustomer, "customer@pilarestilo.com", UserRole.CUSTOMER, false)
        ));
        assertEquals("You can only access your own payments", ex.getMessage());
    }

    @Test
    void get_by_order_id_returns_payment_for_internal_or_owner() {
        PaymentController controller = new PaymentController(queryService);
        UUID orderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        PaymentEntity payment = payment(UUID.randomUUID(), orderId);
        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setCustomerId(ownerId);

        when(queryService.getOrderById(orderId)).thenReturn(order);
        when(queryService.getByOrderId(orderId)).thenReturn(payment);

        PaymentDto ownerResult = controller.getByOrderId(
                orderId,
                new AuthenticatedUser(ownerId, "owner@pilarestilo.com", UserRole.CUSTOMER, false)
        );
        assertEquals(payment.getId(), ownerResult.id());

        PaymentDto internalResult = controller.getByOrderId(
                orderId,
                new AuthenticatedUser(UUID.randomUUID(), "internal@payment-service", UserRole.ADMIN, true)
        );
        assertEquals(payment.getId(), internalResult.id());
    }

    private PaymentEntity payment(UUID paymentId, UUID orderId) {
        PaymentEntity payment = new PaymentEntity();
        payment.setId(paymentId);
        payment.setOrderId(orderId);
        payment.setMethod("TRANSFER");
        payment.setStatus("PENDING");
        payment.setProofReference("proof");
        payment.setTransferAccountHolderName("Pilar Estilo");
        payment.setTransferAccountEmail("admin@pilarestilo.com");
        payment.setTransferAccountNumber("123");
        payment.setTransferAccountType("Corriente");
        payment.setReviewedBy(UUID.randomUUID());
        payment.setReviewedAt(Instant.now());
        payment.setCreatedAt(Instant.now());
        return payment;
    }
}
