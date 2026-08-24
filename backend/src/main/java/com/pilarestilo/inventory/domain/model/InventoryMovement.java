package com.pilarestilo.inventory.domain.model;

import com.pilarestilo.inventory.domain.enums.InventoryMovementType;

import java.time.Instant;
import java.util.UUID;

/**
 * Pure domain model — no JPA, no Spring, no Lombok.
 */
public class InventoryMovement {

    private UUID id;
    private UUID productId;
    private String variantColor;
    private String variantSize;
    private InventoryMovementType type;
    private int quantity;
    private String referenceType;
    private UUID referenceId;
    private UUID recordedBy;
    private String reason;
    private Instant createdAt;

    private InventoryMovement() {}

    /**
     * Factory method for recording a new movement (sets id and createdAt).
     */
    // One parameter per column a movement actually carries.
    @SuppressWarnings("java:S107")
    public static InventoryMovement create(UUID productId,
                                           String variantColor,
                                           String variantSize,
                                           InventoryMovementType type,
                                           int quantity,
                                           String referenceType,
                                           UUID referenceId,
                                           UUID recordedBy,
                                           String reason) {
        InventoryMovement m = new InventoryMovement();
        m.id = UUID.randomUUID();
        m.productId = productId;
        m.variantColor = variantColor;
        m.variantSize = variantSize;
        m.type = type;
        m.quantity = quantity;
        m.referenceType = referenceType;
        m.referenceId = referenceId;
        m.recordedBy = recordedBy;
        m.reason = reason;
        m.createdAt = Instant.now();
        return m;
    }

    /**
     * Reconstruction factory for rebuilding from persistence.
     */
    @SuppressWarnings("java:S107")
    public static InventoryMovement reconstruct(UUID id,
                                                UUID productId,
                                                String variantColor,
                                                String variantSize,
                                                InventoryMovementType type,
                                                int quantity,
                                                String referenceType,
                                                UUID referenceId,
                                                UUID recordedBy,
                                                String reason,
                                                Instant createdAt) {
        InventoryMovement m = new InventoryMovement();
        m.id = id;
        m.productId = productId;
        m.variantColor = variantColor;
        m.variantSize = variantSize;
        m.type = type;
        m.quantity = quantity;
        m.referenceType = referenceType;
        m.referenceId = referenceId;
        m.recordedBy = recordedBy;
        m.reason = reason;
        m.createdAt = createdAt;
        return m;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public String getVariantColor() { return variantColor; }
    public String getVariantSize() { return variantSize; }
    public InventoryMovementType getType() { return type; }
    public int getQuantity() { return quantity; }
    public String getReferenceType() { return referenceType; }
    public UUID getReferenceId() { return referenceId; }
    public UUID getRecordedBy() { return recordedBy; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
