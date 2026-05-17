package com.pilarestilo.inventory.infrastructure.persistence.repositories;

import com.pilarestilo.inventory.domain.model.InventoryMovement;
import com.pilarestilo.inventory.domain.ports.InventoryMovementRepository;
import com.pilarestilo.inventory.infrastructure.persistence.entities.InventoryMovementEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InventoryMovementRepositoryAdapter implements InventoryMovementRepository {

    private final InventoryMovementJpaRepository jpaRepository;

    public InventoryMovementRepositoryAdapter(InventoryMovementJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public InventoryMovement save(InventoryMovement movement) {
        return toDomain(jpaRepository.save(toEntity(movement)));
    }

    @Override
    public Page<InventoryMovement> findByProductId(UUID productId, Pageable pageable) {
        return jpaRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(this::toDomain);
    }

    private InventoryMovementEntity toEntity(InventoryMovement m) {
        InventoryMovementEntity e = new InventoryMovementEntity();
        e.setId(m.getId());
        e.setProductId(m.getProductId());
        e.setVariantColor(m.getVariantColor());
        e.setVariantSize(m.getVariantSize());
        e.setType(m.getType());
        e.setQuantity(m.getQuantity());
        e.setReferenceType(m.getReferenceType());
        e.setReferenceId(m.getReferenceId());
        e.setRecordedBy(m.getRecordedBy());
        e.setReason(m.getReason());
        e.setCreatedAt(m.getCreatedAt());
        return e;
    }

    private InventoryMovement toDomain(InventoryMovementEntity e) {
        return InventoryMovement.reconstruct(
                e.getId(),
                e.getProductId(),
                e.getVariantColor(),
                e.getVariantSize(),
                e.getType(),
                e.getQuantity(),
                e.getReferenceType(),
                e.getReferenceId(),
                e.getRecordedBy(),
                e.getReason(),
                e.getCreatedAt()
        );
    }
}
