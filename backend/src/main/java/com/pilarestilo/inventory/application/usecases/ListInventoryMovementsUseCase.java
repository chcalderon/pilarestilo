package com.pilarestilo.inventory.application.usecases;

import com.pilarestilo.inventory.application.dto.InventoryMovementDto;
import com.pilarestilo.inventory.domain.ports.InventoryMovementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ListInventoryMovementsUseCase {

    private final InventoryMovementRepository inventoryMovementRepository;

    public ListInventoryMovementsUseCase(InventoryMovementRepository inventoryMovementRepository) {
        this.inventoryMovementRepository = inventoryMovementRepository;
    }

    public Page<InventoryMovementDto> execute(UUID productId, Pageable pageable) {
        return inventoryMovementRepository.findByProductId(productId, pageable)
                .map(m -> new InventoryMovementDto(
                        m.getId(),
                        m.getProductId(),
                        m.getVariantColor(),
                        m.getVariantSize(),
                        m.getType().name(),
                        m.getQuantity(),
                        m.getReferenceType(),
                        m.getReferenceId(),
                        m.getRecordedBy(),
                        m.getReason(),
                        m.getCreatedAt()
                ));
    }
}
