package com.pilarestilo.returns.infrastructure.persistence.repositories;

import com.pilarestilo.returns.infrastructure.persistence.entities.ReturnRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReturnRequestJpaRepository extends JpaRepository<ReturnRequestEntity, UUID> {

    /** Mirrors uq_return_requests_open_per_order: closed means REFUNDED or REJECTED. */
    @Query("""
            select r from ReturnRequestEntity r
             where r.orderId = :orderId
               and r.status not in ('REFUNDED', 'REJECTED')
            """)
    Optional<ReturnRequestEntity> findOpenByOrderId(UUID orderId);

    List<ReturnRequestEntity> findByOrderIdOrderByRequestedAtDesc(UUID orderId);

    @Query("""
            select r from ReturnRequestEntity r
             where r.status not in ('REFUNDED', 'REJECTED')
             order by r.deadlineAt asc
            """)
    Page<ReturnRequestEntity> findOpenByDeadline(Pageable pageable);

    List<ReturnRequestEntity> findByRequestedByOrderByRequestedAtDesc(UUID requestedBy);
}
