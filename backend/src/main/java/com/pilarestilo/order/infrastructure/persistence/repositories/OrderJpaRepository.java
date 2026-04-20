package com.pilarestilo.order.infrastructure.persistence.repositories;

import com.pilarestilo.order.infrastructure.persistence.entities.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {

    Page<OrderEntity> findByCustomerId(UUID customerId, Pageable pageable);
}
