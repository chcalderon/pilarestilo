package com.pilarestilo.inventoryservice.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One line of the stock ledger.
 *
 * <p>The table has existed since V57 and was empty in practice: the monolith writes it only on the
 * local path, and production sends every command here instead. So the audit trail covered exactly
 * the configuration nobody runs, and the shop had no record of why a number moved.
 *
 * <p>Deliberately mirrors the monolith's entity rather than sharing one — the two services share a
 * database and no compiler, so the shape is duplicated on purpose and both must change together.
 * The columns are those of V57; nothing here adds to the schema.
 */
@Entity
@Table(name = "inventory_movements")
public class InventoryMovementEntity {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "variant_color", length = 80)
    private String variantColor;

    @Column(name = "variant_size", length = 32)
    private String variantSize;

    /**
     * Stored as text to match the monolith, which maps the same column with an enum. A value it
     * does not know would break its reads, so only RESERVE, CONFIRM, RELEASE and POS_SALE are
     * written here — the four this service performs.
     */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InventoryMovementEntity() {
    }

    private InventoryMovementEntity(UUID productId, String variantColor, String variantSize,
                                    String type, int quantity, String reason, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.productId = productId;
        this.variantColor = variantColor;
        this.variantSize = variantSize;
        this.type = type;
        this.quantity = quantity;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    /**
     * @param quantity signed the way the monolith signs it: positive on RESERVE, negative on
     *                 CONFIRM and RELEASE. The sign reads as direction per line, not as something
     *                 to add up across types — reserving moves units into stock_reserved without
     *                 touching stock_on_hand, so a RESERVE and its CONFIRM sum to zero while the
     *                 shelf went down by one. Filter by type before summing anything.
     */
    public static InventoryMovementEntity record(UUID productId, String variantColor, String variantSize,
                                                 String type, int quantity, String reason, Instant at) {
        return new InventoryMovementEntity(productId, variantColor, variantSize, type, quantity, reason, at);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getVariantColor() {
        return variantColor;
    }

    public String getVariantSize() {
        return variantSize;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
