package com.pilarestilo.inventory.infrastructure.web;

import com.pilarestilo.inventory.application.dto.InventoryMovementDto;
import com.pilarestilo.inventory.application.usecases.ListInventoryMovementsUseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
public class InventoryMovementController {

    private final ListInventoryMovementsUseCase listInventoryMovementsUseCase;

    public InventoryMovementController(ListInventoryMovementsUseCase listInventoryMovementsUseCase) {
        this.listInventoryMovementsUseCase = listInventoryMovementsUseCase;
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public Page<InventoryMovementDto> listMovements(
            @RequestParam UUID productId,
            Pageable pageable) {
        return listInventoryMovementsUseCase.execute(productId, pageable);
    }
}
