package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.rbac.application.RolePermissionResolutionService;
import com.pilarestilo.shared.rbac.domain.model.ResolvedPermissions;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class RefreshTokenUseCase {

    /** Worker vigency is checked against the shop's own calendar day, not the server's zone. */
    private static final ZoneId STORE_ZONE = ZoneId.of("America/Santiago");

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RolePermissionResolutionService rolePermissionResolutionService;

    public RefreshTokenUseCase(JwtTokenProvider jwtTokenProvider,
                               UserRepository userRepository,
                               RolePermissionResolutionService rolePermissionResolutionService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.rolePermissionResolutionService = rolePermissionResolutionService;
    }

    public AuthTokenDto execute(String refreshToken) {
        if (!jwtTokenProvider.isValid(refreshToken)) {
            throw new DomainException("Invalid or expired refresh token");
        }
        Claims claims = jwtTokenProvider.parseToken(refreshToken);
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new DomainException("Token is not a refresh token");
        }
        UUID userId = UUID.fromString(claims.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException("User not found"));
        if (!user.isActive()) {
            throw new DomainException("This account is blocked");
        }

        // A refresh token from before the last password change cannot mint fresh access tokens.
        // Missing "sv" predates the feature and counts as version 1.
        Integer tokenSessionVersion = claims.get("sv", Integer.class);
        int effectiveSessionVersion = tokenSessionVersion != null ? tokenSessionVersion : 1;
        if (effectiveSessionVersion != user.getSessionVersion()) {
            throw new DomainException("Session no longer valid");
        }

        UserRole role = user.getRole();
        if (role != UserRole.ADMIN && role != UserRole.CUSTOMER) {
            LocalDate today = LocalDate.now(STORE_ZONE);
            if (user.getWorkerVigencyEnd() != null && today.isAfter(user.getWorkerVigencyEnd())) {
                throw new DomainException("Worker vigency has expired");
            }
        }
        ResolvedPermissions resolvedPermissions = rolePermissionResolutionService.resolve(role);
        String access     = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                role,
                resolvedPermissions.legacyViewKeys(),
                resolvedPermissions.permissionCodes());
        String newRefresh = jwtTokenProvider.generateRefreshToken(user.getId());
        return AuthTokenDto.of(
                access,
                newRefresh,
                user.getId(),
                user.getEmail(),
                role.name(),
                user.getFullName(),
                user.getAvatarUrl(),
                resolvedPermissions.legacyViewKeys(),
                resolvedPermissions.permissionCodes());
    }
}
