package com.pilarestilo.inventory.infrastructure.persistence.repositories;

import com.pilarestilo.inventory.infrastructure.persistence.entities.InventoryMovementEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryMovementJpaRepository extends JpaRepository<InventoryMovementEntity, UUID> {
    Page<InventoryMovementEntity> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);
}
