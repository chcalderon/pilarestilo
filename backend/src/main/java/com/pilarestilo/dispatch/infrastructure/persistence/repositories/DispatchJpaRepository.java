package com.pilarestilo.dispatch.infrastructure.persistence.repositories;

import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.dispatch.infrastructure.persistence.entities.DispatchEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispatchJpaRepository extends JpaRepository<DispatchEntity, UUID> {
    Optional<DispatchEntity> findByOrderId(UUID orderId);
    List<DispatchEntity> findByStatus(DispatchStatus status);
    List<DispatchEntity> findByDispatcherIdAndStatus(UUID dispatcherId, DispatchStatus status);
    List<DispatchEntity> findByStatusAndDispatchedAtBefore(DispatchStatus status, LocalDateTime dispatchedBefore);
    @Query(
            value = """
                    SELECT * FROM dispatches d
                    WHERE (d.created_at AT TIME ZONE 'America/Santiago')::date BETWEEN :from AND :to
                    ORDER BY d.created_at DESC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM dispatches d
                    WHERE (d.created_at AT TIME ZONE 'America/Santiago')::date BETWEEN :from AND :to
                    """,
            nativeQuery = true
    )
    Page<DispatchEntity> findHistoryByLocalDateRange(@Param("from") LocalDate from,
                                                     @Param("to") LocalDate to,
                                                     Pageable pageable);
    boolean existsByOrderId(UUID orderId);
}
