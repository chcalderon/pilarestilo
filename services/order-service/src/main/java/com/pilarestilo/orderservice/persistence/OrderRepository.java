package com.pilarestilo.orderservice.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = "items")
    Page<OrderEntity> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findById(UUID id);

    @EntityGraph(attributePaths = "items")
    Page<OrderEntity> findByCustomerId(UUID customerId, Pageable pageable);
}
