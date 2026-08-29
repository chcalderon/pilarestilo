package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.application.PasswordResetTokens;
import com.pilarestilo.shared.auth.domain.model.PasswordResetToken;
import com.pilarestilo.shared.auth.domain.ports.PasswordEncoder;
import com.pilarestilo.shared.auth.domain.ports.PasswordResetTokenRepository;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Step two of a self-service reset: consume the link, set the new password, kill every session.
 *
 * <p>A missing, used or expired token all fail with one message — nothing external distinguishes
 * them. Success bumps {@code session_version}, which is what logs the person's other devices out.
 */
@Service
public class ResetPasswordUseCase {

    private static final String INVALID_LINK = "El enlace no es válido o ya expiró";
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ResetPasswordUseCase(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void execute(String rawToken, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new DomainException("La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        if (rawToken == null || rawToken.isBlank()) {
            throw new DomainException(INVALID_LINK);
        }

        Instant now = Instant.now();
        Optional<PasswordResetToken> match = tokenRepository.findByTokenHash(PasswordResetTokens.hash(rawToken));
        if (match.isEmpty() || !match.get().isUsable(now)) {
            throw new DomainException(INVALID_LINK);
        }
        PasswordResetToken token = match.get();

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new DomainException(INVALID_LINK));

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        user.incrementSessionVersion();
        userRepository.save(user);

        token.markUsed(now);
        tokenRepository.save(token);
    }
}
