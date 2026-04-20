package com.pilarestilo.customercredit.domain.events;

import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record StoreCreditGranted(
        UUID customerId,
        Money amount,
        Instant occurredAt
) implements DomainEvent {}
