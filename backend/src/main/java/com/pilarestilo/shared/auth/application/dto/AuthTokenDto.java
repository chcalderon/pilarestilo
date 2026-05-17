package com.pilarestilo.shared.auth.application.dto;

import java.util.List;
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
        boolean accountMerged,
        List<String> permissions,
        List<String> permissionCodes
) {
    public static AuthTokenDto of(String accessToken, String refreshToken,
                                   UUID userId, String email, String role,
                                   String fullName, String avatarUrl,
                                   List<String> permissions) {
        return new AuthTokenDto(accessToken, refreshToken, "Bearer",
                userId, email, role, fullName, avatarUrl, false, permissions, List.of());
    }

    public static AuthTokenDto of(String accessToken, String refreshToken,
                                  UUID userId, String email, String role,
                                  String fullName, String avatarUrl,
                                  List<String> permissions, List<String> permissionCodes) {
        return new AuthTokenDto(accessToken, refreshToken, "Bearer",
                userId, email, role, fullName, avatarUrl, false, permissions, permissionCodes);
    }

    public static AuthTokenDto ofMerged(String accessToken, String refreshToken,
                                        UUID userId, String email, String role,
                                        String fullName, String avatarUrl,
                                        boolean accountMerged, List<String> permissions) {
        return new AuthTokenDto(accessToken, refreshToken, "Bearer",
                userId, email, role, fullName, avatarUrl, accountMerged, permissions, List.of());
    }

    public static AuthTokenDto ofMerged(String accessToken, String refreshToken,
                                        UUID userId, String email, String role,
                                        String fullName, String avatarUrl,
                                        boolean accountMerged,
                                        List<String> permissions,
                                        List<String> permissionCodes) {
        return new AuthTokenDto(accessToken, refreshToken, "Bearer",
                userId, email, role, fullName, avatarUrl, accountMerged, permissions, permissionCodes);
    }
}
