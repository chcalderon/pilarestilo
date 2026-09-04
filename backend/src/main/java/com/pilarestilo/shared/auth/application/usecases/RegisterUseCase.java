package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.discount.application.usecases.IssueWelcomeDiscountUseCase;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RegisterUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegisterUseCase.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RecordConsentUseCase recordConsentUseCase;
    private final AfterCommitPublisher afterCommitPublisher;
    private final IssueWelcomeDiscountUseCase issueWelcomeDiscountUseCase;

    public RegisterUseCase(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           RecordConsentUseCase recordConsentUseCase,
                           AfterCommitPublisher afterCommitPublisher,
                           IssueWelcomeDiscountUseCase issueWelcomeDiscountUseCase) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.recordConsentUseCase = recordConsentUseCase;
        this.afterCommitPublisher = afterCommitPublisher;
        this.issueWelcomeDiscountUseCase = issueWelcomeDiscountUseCase;
    }

    /**
     * @param ipAddress where the acceptance came from, and {@code userAgent} what submitted it.
     *                  Evidence rather than tracking: a consent nobody can place is a consent
     *                  nobody can prove, and proving it is what the Ley 21.719 asks for.
     */
    public AuthTokenDto execute(String email, String rawPassword, String fullName,
                                String ipAddress, String userAgent, boolean acceptsMarketing) {
        /*
         * Normalized before the duplicate check, not just before the save below: without this, a
         * retry with different letter-casing than an existing account (autocapitalized on a phone
         * keyboard, pasted from a signature) sailed past this check and hit the DB's unique
         * constraint on save instead -- an unhandled 500 in place of this method's own message.
         */
        String normalizedEmail = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DomainException("Email already registered: " + normalizedEmail);
        }
        String hash = passwordEncoder.encode(rawPassword);
        User user = User.create(normalizedEmail, fullName, UserRole.CUSTOMER, hash);
        User saved = userRepository.save(user);

        /*
         * Creating the account is the acceptance: the form says so above the button. Recorded
         * against the version published at this moment, because "she accepted the terms" is worth
         * nothing once the terms have been rewritten.
         */
        recordConsentUseCase.execute(saved.getId(), ConsentType.TERMS, ipAddress, userAgent);
        recordConsentUseCase.execute(saved.getId(), ConsentType.PRIVACY, ipAddress, userAgent);
        if (acceptsMarketing) {
            recordConsentUseCase.execute(saved.getId(), ConsentType.MARKETING, ipAddress, userAgent);
        }

        afterCommitPublisher.publish(new UserRegistered(
                saved.getId(), Instant.now(), issueWelcomeDiscount(saved.getId(), acceptsMarketing)));

        List<String> permissions = List.of(); // CUSTOMER has no worker permissions
        List<String> permissionCodes = List.of();
        String access  = jwtTokenProvider.generateAccessToken(
                saved.getId(), saved.getEmail(), saved.getRole(), permissions, permissionCodes,
                saved.getSessionVersion());
        String refresh = jwtTokenProvider.generateRefreshToken(saved.getId(), saved.getSessionVersion());
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

    /**
     * Never lets a broken coupon take the account down with it: she has an account either way, so
     * a failure here is logged and swallowed, not thrown.
     */
    private UserRegistered.WelcomeDiscount issueWelcomeDiscount(UUID userId, boolean acceptsMarketing) {
        try {
            return issueWelcomeDiscountUseCase.issueFor(userId, acceptsMarketing)
                    .map(dto -> new UserRegistered.WelcomeDiscount(
                            dto.code(), dto.type(), dto.value(), dto.minOrderAmount(), dto.validUntil()))
                    .orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Could not issue a welcome discount for user {}: {}", userId, ex.getMessage());
            return null;
        }
    }
}
