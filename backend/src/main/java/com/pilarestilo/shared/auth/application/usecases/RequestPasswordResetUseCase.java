package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.application.PasswordResetTokens;
import com.pilarestilo.shared.auth.domain.model.PasswordResetToken;
import com.pilarestilo.shared.auth.domain.ports.PasswordResetMailer;
import com.pilarestilo.shared.auth.domain.ports.PasswordResetTokenRepository;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Step one of a self-service reset: turn an email address into a link, if it belongs to an account.
 *
 * <p>Enumeration-safe. An address that matches no account produces nothing — no row, no email —
 * and the caller returns the same 200 body either way. A mailer failure is swallowed for the same
 * reason: a 500 on a dead SMTP host would itself signal "this address exists".
 */
@Service
public class RequestPasswordResetUseCase {

    private static final Logger log = LoggerFactory.getLogger(RequestPasswordResetUseCase.class);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetMailer mailer;

    public RequestPasswordResetUseCase(UserRepository userRepository,
                                       PasswordResetTokenRepository tokenRepository,
                                       PasswordResetMailer mailer) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.mailer = mailer;
    }

    public void execute(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        Optional<User> match = userRepository.findByEmail(email.trim().toLowerCase());
        if (match.isEmpty()) {
            return;
        }
        User user = match.get();

        tokenRepository.invalidateUnusedForUser(user.getId());
        String rawToken = PasswordResetTokens.newRawToken();
        tokenRepository.save(PasswordResetToken.issue(user.getId(), PasswordResetTokens.hash(rawToken), TOKEN_TTL));

        try {
            mailer.sendResetLink(user.getEmail(), user.getFullName(), rawToken);
        } catch (RuntimeException e) {
            log.warn("Could not send the password reset email for user {}", user.getId(), e);
        }
    }
}
