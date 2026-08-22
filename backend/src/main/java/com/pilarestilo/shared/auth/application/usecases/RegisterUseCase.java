package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.privacy.domain.enums.ConsentType;
import com.pilarestilo.privacy.application.usecases.RecordConsentUseCase;
import com.pilarestilo.shared.application.AfterCommitPublisher;
import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.domain.ports.PasswordEncoder;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.events.UserRegistered;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class RegisterUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RecordConsentUseCase recordConsentUseCase;
    private final AfterCommitPublisher afterCommitPublisher;

    public RegisterUseCase(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           RecordConsentUseCase recordConsentUseCase,
                           AfterCommitPublisher afterCommitPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.recordConsentUseCase = recordConsentUseCase;
        this.afterCommitPublisher = afterCommitPublisher;
    }

    /**
     * @param ipAddress where the acceptance came from, and {@code userAgent} what submitted it.
     *                  Evidence rather than tracking: a consent nobody can place is a consent
     *                  nobody can prove, and proving it is what the Ley 21.719 asks for.
     */
    public AuthTokenDto execute(String email, String rawPassword, String fullName,
                                String ipAddress, String userAgent) {
        if (userRepository.existsByEmail(email)) {
            throw new DomainException("Email already registered: " + email);
        }
        String hash = passwordEncoder.encode(rawPassword);
        User user = User.create(email, fullName, UserRole.CUSTOMER, hash);
        User saved = userRepository.save(user);

        /*
         * Creating the account is the acceptance: the form says so above the button. Recorded
         * against the version published at this moment, because "she accepted the terms" is worth
         * nothing once the terms have been rewritten.
         */
        recordConsentUseCase.execute(saved.getId(), ConsentType.TERMS, ipAddress, userAgent);
        recordConsentUseCase.execute(saved.getId(), ConsentType.PRIVACY, ipAddress, userAgent);

        afterCommitPublisher.publish(new UserRegistered(saved.getId(), Instant.now()));

        List<String> permissions = List.of(); // CUSTOMER has no worker permissions
        List<String> permissionCodes = List.of();
        String access  = jwtTokenProvider.generateAccessToken(
                saved.getId(), saved.getEmail(), saved.getRole(), permissions, permissionCodes);
        String refresh = jwtTokenProvider.generateRefreshToken(saved.getId());
        return AuthTokenDto.of(
                access,
                refresh,
                saved.getId(),
                saved.getEmail(),
                saved.getRole().name(),
                saved.getFullName(),
                saved.getAvatarUrl(),
                permissions,
                permissionCodes);
    }
}
