package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.domain.ports.PasswordEncoder;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RegisterUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public RegisterUseCase(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public AuthTokenDto execute(String email, String rawPassword, String fullName) {
        if (userRepository.existsByEmail(email)) {
            throw new DomainException("Email already registered: " + email);
        }
        String hash = passwordEncoder.encode(rawPassword);
        User user = User.create(email, fullName, UserRole.CUSTOMER, hash);
        User saved = userRepository.save(user);

        List<String> permissions = List.of(); // CUSTOMER has no worker permissions
        String access  = jwtTokenProvider.generateAccessToken(saved.getId(), saved.getEmail(), saved.getRole(), permissions);
        String refresh = jwtTokenProvider.generateRefreshToken(saved.getId());
        return AuthTokenDto.of(access, refresh, saved.getId(), saved.getEmail(), saved.getRole().name(), saved.getFullName(), saved.getAvatarUrl(), permissions);
    }
}
