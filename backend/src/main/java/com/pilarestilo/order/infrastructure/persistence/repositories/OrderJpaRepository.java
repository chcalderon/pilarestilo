package com.pilarestilo.order.infrastructure.persistence.repositories;

import com.pilarestilo.order.infrastructure.persistence.entities.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {

    /**
     * OrderEntity.items is lazy, and OrderRepositoryAdapter.toDomain() always walks it, so every
     * caller outside an open Hibernate session hit LazyInitializationException — the Kafka
     * listeners for PaymentConfirmed did, and open-in-view is disabled. Fetching items with the
     * order makes this lookup independent of an ambient session.
     *
     * Deliberately not applied to findAll: a collection join plus Pageable makes Hibernate
     * paginate in memory.
     */
    @Override
    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findById(UUID id);

    Page<OrderEntity> findByCustomerId(UUID customerId, Pageable pageable);
}
