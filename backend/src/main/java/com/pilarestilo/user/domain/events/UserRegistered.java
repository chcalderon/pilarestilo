package com.pilarestilo.user.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record UserRegistered(
        UUID userId,
        Instant occurredAt
) implements DomainEvent {}
