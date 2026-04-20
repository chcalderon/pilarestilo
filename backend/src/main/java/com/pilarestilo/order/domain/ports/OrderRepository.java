package com.pilarestilo.order.domain.ports;

import com.pilarestilo.order.domain.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    Page<Order> findAll(Pageable pageable);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);
}
