package com.pilarestilo.shared.domain;

import java.time.Instant;

public interface DomainEvent {
    Instant occurredAt();
}
