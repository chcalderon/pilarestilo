package com.pilarestilo.inventory.domain.ports;

import com.pilarestilo.inventory.domain.model.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InventoryMovementRepository {
    InventoryMovement save(InventoryMovement movement);
    Page<InventoryMovement> findByProductId(UUID productId, Pageable pageable);
}
