package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.domain.ports.PasswordEncoder;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginUseCase(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public AuthTokenDto execute(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new DomainException("Invalid credentials"));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new DomainException("Invalid credentials");
        }
        String access  = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refresh = jwtTokenProvider.generateRefreshToken(user.getId());
        return AuthTokenDto.of(access, refresh, user.getId(), user.getEmail(), user.getRole().name());
    }
}
