package com.pilarestilo.user.application.dto;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String fullName,
        String role,
        Instant createdAt
) {}
