package com.pilarestilo.order.application.usecases;

import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.mappers.OrderMapper;
import com.pilarestilo.order.application.remote.OrderRemoteQueryClient;
import com.pilarestilo.order.domain.ports.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class GetOrderUseCase {

    private final OrderRepository orderRepository;
    private final OrderRemoteQueryClient orderRemoteQueryClient;

    public GetOrderUseCase(OrderRepository orderRepository,
                           OrderRemoteQueryClient orderRemoteQueryClient) {
        this.orderRepository = orderRepository;
        this.orderRemoteQueryClient = orderRemoteQueryClient;
    }

    @Transactional(readOnly = true)
    public OrderDto execute(UUID id) {
        if (orderRemoteQueryClient.isEnabled()) {
            return orderRemoteQueryClient.getById(id)
                    .orElseThrow(() -> new NoSuchElementException("Order not found: " + id));
        }
        return orderRepository.findById(id)
                .map(OrderMapper::toDto)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + id));
    }
}
