package com.pilarestilo.dispatch.domain.ports;

import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispatchRepository {
    Dispatch save(Dispatch dispatch);
    Optional<Dispatch> findById(UUID id);
    Optional<Dispatch> findByOrderId(UUID orderId);
    List<Dispatch> findByStatus(DispatchStatus status);
    List<Dispatch> findByDispatcherIdAndStatus(UUID dispatcherId, DispatchStatus status);
    List<Dispatch> findByStatusAndDispatchedAtBefore(DispatchStatus status, LocalDateTime dispatchedBefore);
    Page<Dispatch> findAll(Pageable pageable);
    Page<Dispatch> findHistory(LocalDate from, LocalDate to, Pageable pageable);
    boolean existsByOrderId(UUID orderId);
}
