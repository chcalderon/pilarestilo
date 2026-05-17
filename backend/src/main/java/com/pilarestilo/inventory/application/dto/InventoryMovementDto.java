package com.pilarestilo.inventory.application.dto;

import java.time.Instant;
import java.util.UUID;

public record InventoryMovementDto(
        UUID id,
        UUID productId,
        String variantColor,
        String variantSize,
        String type,
        int quantity,
        String referenceType,
        UUID referenceId,
        UUID recordedBy,
        String reason,
        Instant createdAt
) {}
