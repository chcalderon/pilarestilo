package com.pilarestilo.notificationservice.domain.view;

import java.util.UUID;

/** Read-only projection of {@code users}: only what a notification recipient needs. */
public record CustomerView(
        UUID id,
        String email,
        String phone,
        String fullName,
        String role,
        boolean active,
        String notificationChannelPreference) {
}
