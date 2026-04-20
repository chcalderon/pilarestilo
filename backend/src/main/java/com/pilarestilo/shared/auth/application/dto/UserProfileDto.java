package com.pilarestilo.shared.auth.application.dto;

import java.util.UUID;

public record UserProfileDto(
        UUID id,
        String email,
        String fullName,
        String role,
        boolean active
) {}
