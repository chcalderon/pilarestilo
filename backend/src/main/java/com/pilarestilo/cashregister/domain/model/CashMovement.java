package com.pilarestilo.cashregister.domain.model;

import com.pilarestilo.cashregister.domain.enums.CashMovementCategory;
import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

public class CashMovement {

    /** Cash movement timestamps are the shop's own, not whatever zone the server happens to run in. */
    private static final ZoneId STORE_ZONE = ZoneId.of("America/Santiago");

    private UUID id;
    private UUID cashRegisterId;
    private CashMovementType type;
    private CashMovementCategory category;
    private BigDecimal amount;
    private String description;
    private UUID orderId;
    private LocalDateTime recordedAt;
    private UUID recordedBy;

    private CashMovement() {}

    public static CashMovement create(UUID cashRegisterId, CashMovementType type,
                                       CashMovementCategory category,
                                       BigDecimal amount, String description,
                                       UUID orderId, UUID recordedBy) {
        CashMovement m = new CashMovement();
        m.id = UUID.randomUUID();
        m.cashRegisterId = cashRegisterId;
        m.type = type;
        m.category = category;
        m.amount = amount;
        m.description = description;
        m.orderId = orderId;
        m.recordedAt = LocalDateTime.now(STORE_ZONE);
        m.recordedBy = recordedBy;
        return m;
    }

    // One parameter per column a movement actually carries; rehydration follows the schema.
    @SuppressWarnings("java:S107")
    public static CashMovement reconstruct(UUID id, UUID cashRegisterId, CashMovementType type,
                                            CashMovementCategory category,
                                            BigDecimal amount, String description, UUID orderId,
                                            LocalDateTime recordedAt, UUID recordedBy) {
        CashMovement m = new CashMovement();
        m.id = id; m.cashRegisterId = cashRegisterId; m.type = type;
        m.category = category;
        m.amount = amount; m.description = description; m.orderId = orderId;
        m.recordedAt = recordedAt; m.recordedBy = recordedBy;
        return m;
    }

    public UUID getId() { return id; }
    public UUID getCashRegisterId() { return cashRegisterId; }
    public CashMovementType getType() { return type; }
    public CashMovementCategory getCategory() { return category; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public UUID getOrderId() { return orderId; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public UUID getRecordedBy() { return recordedBy; }
}
