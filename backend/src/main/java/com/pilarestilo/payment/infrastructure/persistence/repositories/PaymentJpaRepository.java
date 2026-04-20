package com.pilarestilo.payment.infrastructure.persistence.repositories;

import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.infrastructure.persistence.entities.PaymentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByOrderId(UUID orderId);

    Page<PaymentEntity> findByStatus(PaymentStatus status, Pageable pageable);
}
