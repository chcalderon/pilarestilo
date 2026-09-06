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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Step two of a self-service reset: check the emailed code, set the new password, kill every
 * session.
 *
 * <p>An unknown email, a wrong code, an expired or used code, and a code that ran out of attempts
 * all fail with the one message — nothing external distinguishes them. A wrong code burns one of
 * {@link PasswordResetToken#MAX_ATTEMPTS} tries. Success bumps {@code session_version}, which is
 * what logs the person's other devices out.
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
    public void execute(String email, String code, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new DomainException(
                    "La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            throw new DomainException(INVALID_LINK);
        }

        User user = userRepository.findByEmail(User.normalizeEmail(email)).orElse(null);
        if (user == null) {
            throw new DomainException(INVALID_LINK);
        }

        PasswordResetToken token = tokenRepository.findActiveByUserId(user.getId()).orElse(null);
        if (token == null || !token.isUsable(Instant.now())) {
            throw new DomainException(INVALID_LINK);
        }

        // Constant-time compare so a timing side-channel does not leak a code prefix.
        boolean matches = MessageDigest.isEqual(
                token.getTokenHash().getBytes(StandardCharsets.UTF_8),
                PasswordResetTokens.hash(code).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            // Its own transaction — this @Transactional method is about to roll back.
            tokenRepository.recordFailedAttempt(token.getId());
            throw new DomainException(INVALID_LINK);
        }

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        user.incrementSessionVersion();
        userRepository.save(user);

        token.markUsed(Instant.now());
        tokenRepository.save(token);
    }
}
