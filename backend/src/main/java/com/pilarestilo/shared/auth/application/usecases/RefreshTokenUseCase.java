package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RefreshTokenUseCase {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public RefreshTokenUseCase(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
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

        String access  = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String newRefresh = jwtTokenProvider.generateRefreshToken(user.getId());
        return AuthTokenDto.of(access, newRefresh, user.getId(), user.getEmail(), user.getRole().name());
    }
}
