package com.pilarestilo.paymentservice.application;

import com.pilarestilo.paymentservice.persistence.OrderEntity;
import com.pilarestilo.paymentservice.persistence.OrderRepository;
import com.pilarestilo.paymentservice.persistence.PaymentEntity;
import com.pilarestilo.paymentservice.persistence.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderRepository orderRepository;

    @Test
    void list_with_status_uses_filtered_query() {
        PaymentQueryService service = new PaymentQueryService(paymentRepository, orderRepository);
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<PaymentEntity> expected = new PageImpl<>(List.of(new PaymentEntity()));
        when(paymentRepository.findByStatus("PENDING", pageRequest)).thenReturn(expected);

        Page<PaymentEntity> result = service.list(" pending ", pageRequest);

        assertSame(expected, result);
        verify(paymentRepository).findByStatus("PENDING", pageRequest);
    }

    @Test
    void list_without_status_uses_find_all() {
        PaymentQueryService service = new PaymentQueryService(paymentRepository, orderRepository);
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<PaymentEntity> expected = new PageImpl<>(List.of(new PaymentEntity()));
        when(paymentRepository.findAll(pageRequest)).thenReturn(expected);

        Page<PaymentEntity> result = service.list(" ", pageRequest);

        assertSame(expected, result);
        verify(paymentRepository).findAll(pageRequest);
    }

    @Test
    void get_by_id_and_order_id_throw_when_missing() {
        PaymentQueryService service = new PaymentQueryService(paymentRepository, orderRepository);
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        NoSuchElementException paymentEx = assertThrows(NoSuchElementException.class, () -> service.getById(paymentId));
        assertEquals("Payment not found: " + paymentId, paymentEx.getMessage());

        NoSuchElementException paymentByOrderEx = assertThrows(NoSuchElementException.class, () -> service.getByOrderId(orderId));
        assertEquals("Payment not found for order: " + orderId, paymentByOrderEx.getMessage());

        NoSuchElementException orderEx = assertThrows(NoSuchElementException.class, () -> service.getOrderById(orderId));
        assertEquals("Order not found: " + orderId, orderEx.getMessage());
    }

    @Test
    void get_by_id_and_order_id_return_entities() {
        PaymentQueryService service = new PaymentQueryService(paymentRepository, orderRepository);
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentEntity payment = new PaymentEntity();
        OrderEntity order = new OrderEntity();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertSame(payment, service.getById(paymentId));
        assertSame(payment, service.getByOrderId(orderId));
        assertSame(order, service.getOrderById(orderId));
    }
}
