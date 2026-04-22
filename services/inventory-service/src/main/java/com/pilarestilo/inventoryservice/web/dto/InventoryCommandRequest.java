package com.pilarestilo.inventoryservice.web.dto;

import java.util.UUID;

public record InventoryCommandRequest(
        UUID productId,
        int qty
) {
}
