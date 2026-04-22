package com.pilarestilo.orderservice.application;

import com.pilarestilo.orderservice.persistence.OrderEntity;
import com.pilarestilo.orderservice.persistence.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Page<OrderEntity> list(UUID customerId, Pageable pageable) {
        if (customerId != null) {
            return orderRepository.findByCustomerId(customerId, pageable);
        }
        return orderRepository.findAll(pageable);
    }

    public OrderEntity getById(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Order not found: " + id));
    }
}
