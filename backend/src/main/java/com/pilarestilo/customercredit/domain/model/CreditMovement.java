package com.pilarestilo.customercredit.domain.model;

import com.pilarestilo.customercredit.domain.enums.CreditMovementType;
import com.pilarestilo.shared.application.Money;

import java.time.Instant;
import java.util.UUID;

public class CreditMovement {

    private UUID id;
    private UUID customerId;
    private CreditMovementType type;
    private Money amount;
    private String reason;
    private Instant createdAt;

    private CreditMovement() {}

    public static CreditMovement of(UUID customerId, CreditMovementType type, Money amount, String reason) {
        CreditMovement m = new CreditMovement();
        m.id = UUID.randomUUID();
        m.customerId = customerId;
        m.type = type;
        m.amount = amount;
        m.reason = reason;
        m.createdAt = Instant.now();
        return m;
    }

    public static CreditMovement reconstruct(UUID id, UUID customerId, CreditMovementType type,
                                              Money amount, String reason, Instant createdAt) {
        CreditMovement m = new CreditMovement();
        m.id = id;
        m.customerId = customerId;
        m.type = type;
        m.amount = amount;
        m.reason = reason;
        m.createdAt = createdAt;
        return m;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public CreditMovementType getType() { return type; }
    public Money getAmount() { return amount; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
