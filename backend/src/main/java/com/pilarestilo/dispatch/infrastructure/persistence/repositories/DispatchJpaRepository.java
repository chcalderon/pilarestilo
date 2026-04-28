package com.pilarestilo.dispatch.infrastructure.persistence.repositories;

import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.dispatch.infrastructure.persistence.entities.DispatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispatchJpaRepository extends JpaRepository<DispatchEntity, UUID> {
    Optional<DispatchEntity> findByOrderId(UUID orderId);
    List<DispatchEntity> findByStatus(DispatchStatus status);
    List<DispatchEntity> findByDispatcherIdAndStatus(UUID dispatcherId, DispatchStatus status);
    boolean existsByOrderId(UUID orderId);
}
