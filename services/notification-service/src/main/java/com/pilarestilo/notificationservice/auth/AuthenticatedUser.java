package com.pilarestilo.notificationservice.auth;

import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String email,
        UserRole role,
        boolean internalCall
) {
}
