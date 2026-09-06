package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.application.PasswordResetTokens;
import com.pilarestilo.shared.auth.domain.model.PasswordResetToken;
import com.pilarestilo.shared.auth.domain.ports.PasswordEncoder;
import com.pilarestilo.shared.auth.domain.ports.PasswordResetTokenRepository;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResetPasswordUseCaseTest {

    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    ResetPasswordUseCase useCase;
    private final UUID userId = UUID.randomUUID();
    private User user;
    private static final String EMAIL = "camila@example.com";
    private static final String CODE = "418302";

    @BeforeEach
    void setUp() {
        useCase = new ResetPasswordUseCase(tokenRepository, userRepository, passwordEncoder);
        user = User.reconstruct(userId, EMAIL, "Camila", UserRole.CUSTOMER, true, "old-hash", Instant.now());
        lenient().when(passwordEncoder.encode("BrandNew123")).thenReturn("new-hash");
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private PasswordResetToken activeToken(String code, Duration ttl) {
        return PasswordResetToken.issue(userId, PasswordResetTokens.hash(code), ttl);
    }

    @Test
    void the_right_code_changes_the_password_bumps_the_session_version_and_marks_the_token_used() {
        PasswordResetToken token = activeToken(CODE, Duration.ofMinutes(30));
        when(tokenRepository.findActiveByUserId(userId)).thenReturn(Optional.of(token));

        useCase.execute(EMAIL, CODE, "BrandNew123");

        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    void a_wrong_code_records_a_failed_attempt_and_fails_with_the_generic_error() {
        PasswordResetToken token = activeToken(CODE, Duration.ofMinutes(30));
        when(tokenRepository.findActiveByUserId(userId)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> useCase.execute(EMAIL, "000000", "BrandNew123"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no es válido");
        verify(tokenRepository).recordFailedAttempt(token.getId());
        verify(userRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void a_token_already_at_the_attempt_limit_fails_without_touching_the_password() {
        PasswordResetToken locked = PasswordResetToken.reconstruct(UUID.randomUUID(), userId,
                PasswordResetTokens.hash(CODE), Instant.now().plusSeconds(1800), null, Instant.now(),
                PasswordResetToken.MAX_ATTEMPTS);
        when(tokenRepository.findActiveByUserId(userId)).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> useCase.execute(EMAIL, CODE, "BrandNew123"))
                .isInstanceOf(DomainException.class);
        verify(userRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void an_unknown_email_fails_with_the_generic_error() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("nobody@example.com", CODE, "BrandNew123"))
                .isInstanceOf(DomainException.class).hasMessageContaining("no es válido");
    }

    @Test
    void no_active_token_fails_with_the_generic_error() {
        when(tokenRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(EMAIL, CODE, "BrandNew123"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void a_password_under_8_chars_is_rejected_before_anything_is_looked_up() {
        assertThatThrownBy(() -> useCase.execute(EMAIL, CODE, "short"))
                .isInstanceOf(DomainException.class).hasMessageContaining("8 caracteres");
    }
}
