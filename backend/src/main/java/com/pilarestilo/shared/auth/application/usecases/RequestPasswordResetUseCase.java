package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.application.PasswordResetTokens;
import com.pilarestilo.shared.auth.domain.model.PasswordResetToken;
import com.pilarestilo.shared.auth.domain.ports.PasswordResetMailer;
import com.pilarestilo.shared.auth.domain.ports.PasswordResetTokenRepository;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Step one of a self-service reset: turn an email address into a 6-digit code, if it belongs to
 * an account.
 *
 * <p>Enumeration-safe. An address that matches no account produces nothing — no row, no email —
 * and the caller returns the same 200 body either way. A mailer failure is swallowed for the same
 * reason: a 500 on a dead SMTP host would itself signal "this address exists".
 */
@Service
public class RequestPasswordResetUseCase {

    private static final Logger log = LoggerFactory.getLogger(RequestPasswordResetUseCase.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetMailer mailer;
    private final Duration codeTtl;

    public RequestPasswordResetUseCase(UserRepository userRepository,
                                       PasswordResetTokenRepository tokenRepository,
                                       PasswordResetMailer mailer,
                                       @Value("${app.password-reset.code-ttl-minutes:30}") int codeTtlMinutes) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.mailer = mailer;
        this.codeTtl = Duration.ofMinutes(codeTtlMinutes);
    }

    public void execute(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        Optional<User> match = userRepository.findByEmail(User.normalizeEmail(email));
        if (match.isEmpty()) {
            return;
        }
        User user = match.get();

        tokenRepository.invalidateUnusedForUser(user.getId());
        String code = PasswordResetTokens.newCode();
        tokenRepository.save(PasswordResetToken.issue(user.getId(), PasswordResetTokens.hash(code), codeTtl));

        try {
            mailer.sendResetCode(user.getEmail(), user.getFullName(), code);
        } catch (RuntimeException e) {
            log.warn("Could not send the password reset email for user {}", user.getId(), e);
        }
    }
}
