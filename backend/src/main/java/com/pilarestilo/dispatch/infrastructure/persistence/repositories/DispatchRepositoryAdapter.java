package com.pilarestilo.dispatch.infrastructure.persistence.repositories;

import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.dispatch.infrastructure.persistence.entities.DispatchEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DispatchRepositoryAdapter implements DispatchRepository {

    private final DispatchJpaRepository jpaRepository;

    public DispatchRepositoryAdapter(DispatchJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override public Dispatch save(Dispatch d) { return toDomain(jpaRepository.save(toEntity(d))); }
    @Override public Optional<Dispatch> findById(UUID id) { return jpaRepository.findById(id).map(this::toDomain); }
    @Override public Optional<Dispatch> findByOrderId(UUID orderId) { return jpaRepository.findByOrderId(orderId).map(this::toDomain); }
    @Override public List<Dispatch> findByStatus(DispatchStatus status) { return jpaRepository.findByStatus(status).stream().map(this::toDomain).toList(); }
    @Override public List<Dispatch> findByDispatcherIdAndStatus(UUID dispatcherId, DispatchStatus status) { return jpaRepository.findByDispatcherIdAndStatus(dispatcherId, status).stream().map(this::toDomain).toList(); }
    @Override public Page<Dispatch> findAll(Pageable pageable) { return jpaRepository.findAll(pageable).map(this::toDomain); }
    @Override public boolean existsByOrderId(UUID orderId) { return jpaRepository.existsByOrderId(orderId); }

    private DispatchEntity toEntity(Dispatch d) {
        DispatchEntity e = new DispatchEntity();
        e.setId(d.getId()); e.setOrderId(d.getOrderId()); e.setDispatcherId(d.getDispatcherId());
        e.setStatus(d.getStatus()); e.setCarrier(d.getCarrier()); e.setTrackingCode(d.getTrackingCode());
        e.setScheduledDate(d.getScheduledDate()); e.setDispatchedAt(d.getDispatchedAt());
        e.setDeliveredAt(d.getDeliveredAt()); e.setNotes(d.getNotes()); e.setCreatedAt(d.getCreatedAt());
        return e;
    }

    private Dispatch toDomain(DispatchEntity e) {
        return Dispatch.reconstruct(e.getId(), e.getOrderId(), e.getDispatcherId(),
                e.getStatus(), e.getCarrier(), e.getTrackingCode(), e.getScheduledDate(),
                e.getDispatchedAt(), e.getDeliveredAt(), e.getNotes(), e.getCreatedAt());
    }
}
