package com.pilarestilo.payment.infrastructure.persistence.repositories;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.infrastructure.persistence.entities.PaymentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByOrderId(UUID orderId);

    Page<PaymentEntity> findByStatus(PaymentStatus status, Pageable pageable);

    @Query("SELECT p FROM PaymentEntity p WHERE p.status = :status AND p.method = :method AND p.createdAt < :cutoff ORDER BY p.createdAt ASC LIMIT :limit")
    List<PaymentEntity> findPendingBankTransfersOlderThan(
            @Param("status") PaymentStatus status,
            @Param("method") PaymentMethod method,
            @Param("cutoff") Instant cutoff,
            @Param("limit") int limit
    );
}
