package com.pilarestilo.inventory.application;

import com.pilarestilo.inventory.application.dto.InventoryMovementDto;
import com.pilarestilo.inventory.application.usecases.ListInventoryMovementsUseCase;
import com.pilarestilo.inventory.domain.enums.InventoryMovementType;
import com.pilarestilo.inventory.domain.model.InventoryMovement;
import com.pilarestilo.inventory.domain.ports.InventoryMovementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListInventoryMovementsUseCaseTest {

    @Mock
    private InventoryMovementRepository inventoryMovementRepository;

    @InjectMocks
    private ListInventoryMovementsUseCase useCase;

    @Test
    void execute_returnsCorrectDtoPage() {
        UUID productId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Instant now = Instant.now();

        InventoryMovement movement = InventoryMovement.reconstruct(
                movementId, productId, "Azul", "L",
                InventoryMovementType.RESERVE, 3,
                null, null, null, null, now
        );

        when(inventoryMovementRepository.findByProductId(productId, pageable))
                .thenReturn(new PageImpl<>(List.of(movement), pageable, 1));

        Page<InventoryMovementDto> result = useCase.execute(productId, pageable);

        assertEquals(1, result.getTotalElements());
        InventoryMovementDto dto = result.getContent().get(0);
        assertEquals(movementId, dto.id());
        assertEquals(productId, dto.productId());
        assertEquals("Azul", dto.variantColor());
        assertEquals("L", dto.variantSize());
        assertEquals("RESERVE", dto.type());
        assertEquals(3, dto.quantity());
        assertEquals(now, dto.createdAt());

        verify(inventoryMovementRepository).findByProductId(productId, pageable);
    }

    @Test
    void execute_delegatesToRepositoryWithCorrectArgs() {
        UUID productId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(1, 5);

        when(inventoryMovementRepository.findByProductId(productId, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<InventoryMovementDto> result = useCase.execute(productId, pageable);

        assertEquals(0, result.getTotalElements());
        verify(inventoryMovementRepository).findByProductId(productId, pageable);
    }
}
