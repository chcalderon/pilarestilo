package com.pilarestilo.shared.auth.application.dto;

import java.util.UUID;

public record AuthTokenDto(
        String accessToken,
        String refreshToken,
        String tokenType,
        UUID userId,
        String email,
        String role,
        String fullName,
        String avatarUrl,
        boolean accountMerged
) {
    public static AuthTokenDto of(String accessToken, String refreshToken,
                                   UUID userId, String email, String role, String fullName, String avatarUrl) {
        return new AuthTokenDto(accessToken, refreshToken, "Bearer", userId, email, role, fullName, avatarUrl, false);
    }

    public static AuthTokenDto ofMerged(String accessToken, String refreshToken,
                                        UUID userId, String email, String role, String fullName, String avatarUrl,
                                        boolean accountMerged) {
        return new AuthTokenDto(accessToken, refreshToken, "Bearer", userId, email, role, fullName, avatarUrl, accountMerged);
    }
}
