package com.pilarestilo.orderservice.application;

import com.pilarestilo.orderservice.persistence.OrderEntity;
import com.pilarestilo.orderservice.persistence.OrderRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Test
    void list_with_customer_id_uses_find_by_customer_id() {
        OrderQueryService service = new OrderQueryService(orderRepository);
        UUID customerId = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<OrderEntity> expected = new PageImpl<>(List.of(new OrderEntity()));

        when(orderRepository.findByCustomerId(eq(customerId), eq(pageRequest))).thenReturn(expected);

        Page<OrderEntity> result = service.list(customerId, pageRequest);

        assertSame(expected, result);
        verify(orderRepository).findByCustomerId(customerId, pageRequest);
    }

    @Test
    void list_without_customer_id_uses_find_all() {
        OrderQueryService service = new OrderQueryService(orderRepository);
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<OrderEntity> expected = new PageImpl<>(List.of(new OrderEntity()));

        when(orderRepository.findAll(eq(pageRequest))).thenReturn(expected);

        Page<OrderEntity> result = service.list(null, pageRequest);

        assertSame(expected, result);
        verify(orderRepository).findAll(pageRequest);
    }

    @Test
    void get_by_id_returns_order_when_found() {
        OrderQueryService service = new OrderQueryService(orderRepository);
        UUID orderId = UUID.randomUUID();
        OrderEntity expected = new OrderEntity();
        expected.setId(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(expected));

        OrderEntity result = service.getById(orderId);

        assertSame(expected, result);
    }

    @Test
    void get_by_id_throws_when_not_found() {
        OrderQueryService service = new OrderQueryService(orderRepository);
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        NoSuchElementException ex = assertThrows(NoSuchElementException.class, () -> service.getById(orderId));

        assertEquals("Order not found: " + orderId, ex.getMessage());
    }
}
