package com.pilarestilo.shared.auth.domain;

import com.pilarestilo.user.domain.enums.UserRole;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, UserRole role) {}
