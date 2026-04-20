package com.pilarestilo.customercredit.domain.model;

import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;

import java.util.UUID;

public class CustomerCredit {

    private UUID id;
    private UUID customerId;
    private Money balance;

    private CustomerCredit() {}

    public static CustomerCredit create(UUID customerId) {
        CustomerCredit cc = new CustomerCredit();
        cc.id = UUID.randomUUID();
        cc.customerId = customerId;
        cc.balance = Money.zero();
        return cc;
    }

    public static CustomerCredit reconstruct(UUID id, UUID customerId, Money balance) {
        CustomerCredit cc = new CustomerCredit();
        cc.id = id;
        cc.customerId = customerId;
        cc.balance = balance;
        return cc;
    }

    public void grant(Money amount) {
        this.balance = this.balance.add(amount);
    }

    public void use(Money amount) {
        if (!this.balance.isGreaterThanOrEqualTo(amount)) {
            throw new DomainException("Insufficient store credit. Available: "
                    + this.balance.amount() + ", requested: " + amount.amount());
        }
        this.balance = this.balance.subtract(amount);
    }

    public void adjust(Money newBalance) {
        this.balance = newBalance;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public Money getBalance() { return balance; }
}
