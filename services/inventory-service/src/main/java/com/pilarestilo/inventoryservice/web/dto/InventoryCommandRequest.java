package com.pilarestilo.inventoryservice.web.dto;

import java.util.UUID;

/**
 * A stock command from the monolith, and what caused it.
 *
 * <p>The three cause fields mirror {@code StockMovementOrigin} there. When the monolith delegates
 * the write, this service is the one that writes the ledger line, so a payload without them
 * produces a movement nobody can trace back to a sale, a return or a person — which is what every
 * row in inventory_movements looked like until now.
 */
public record InventoryCommandRequest(
        UUID productId,
        int qty,
        String variantColor,
        String variantSize,
        String referenceType,
        UUID referenceId,
        UUID recordedBy
) {
}
