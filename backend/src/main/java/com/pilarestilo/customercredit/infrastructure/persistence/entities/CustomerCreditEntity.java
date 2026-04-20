package com.pilarestilo.customercredit.infrastructure.persistence.entities;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "customer_credits")
public class CustomerCreditEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, unique = true)
    private UUID customerId;

    @Column(name = "balance_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAmount;

    @Column(name = "balance_currency", nullable = false, length = 10)
    private String balanceCurrency;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public BigDecimal getBalanceAmount() { return balanceAmount; }
    public void setBalanceAmount(BigDecimal balanceAmount) { this.balanceAmount = balanceAmount; }

    public String getBalanceCurrency() { return balanceCurrency; }
    public void setBalanceCurrency(String balanceCurrency) { this.balanceCurrency = balanceCurrency; }
}
