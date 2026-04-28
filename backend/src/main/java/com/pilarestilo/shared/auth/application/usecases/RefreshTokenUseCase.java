package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionRepository;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenUseCase {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public RefreshTokenUseCase(JwtTokenProvider jwtTokenProvider,
                               UserRepository userRepository,
                               RolePermissionRepository rolePermissionRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.rolePermissionRepository = rolePermissionRepository;
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

        UserRole role = user.getRole();
        if (role != UserRole.ADMIN && role != UserRole.CUSTOMER) {
            LocalDate today = LocalDate.now();
            if (user.getWorkerVigencyEnd() != null && today.isAfter(user.getWorkerVigencyEnd())) {
                throw new DomainException("Worker vigency has expired");
            }
        }
        List<String> permissions = rolePermissionRepository.findViewKeysByRole(role);
        String access     = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), role, permissions);
        String newRefresh = jwtTokenProvider.generateRefreshToken(user.getId());
        return AuthTokenDto.of(access, newRefresh, user.getId(), user.getEmail(), role.name(), user.getFullName(), user.getAvatarUrl(), permissions);
    }
}
