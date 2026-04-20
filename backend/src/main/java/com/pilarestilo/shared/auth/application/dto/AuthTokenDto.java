package com.pilarestilo.shared.auth.application.dto;

import java.util.UUID;

public record AuthTokenDto(
        String accessToken,
        String refreshToken,
        String tokenType,
        UUID userId,
        String email,
        String role
) {
    public static AuthTokenDto of(String accessToken, String refreshToken,
                                   UUID userId, String email, String role) {
        return new AuthTokenDto(accessToken, refreshToken, "Bearer", userId, email, role);
    }
}
