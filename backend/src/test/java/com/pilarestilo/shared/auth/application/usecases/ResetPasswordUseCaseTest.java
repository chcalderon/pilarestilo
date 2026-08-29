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
import static org.mockito.ArgumentMatchers.argThat;
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

    @BeforeEach
    void setUp() {
        useCase = new ResetPasswordUseCase(tokenRepository, userRepository, passwordEncoder);
        user = User.reconstruct(userId, "camila@example.com", "Camila", UserRole.CUSTOMER, true, "old-hash", Instant.now());
        lenient().when(passwordEncoder.encode("BrandNew123")).thenReturn("new-hash");
    }

    private PasswordResetToken tokenFor(String raw, Duration ttl) {
        return PasswordResetToken.issue(userId, PasswordResetTokens.hash(raw), ttl);
    }

    @Test
    void a_valid_token_changes_the_password_bumps_the_session_version_and_marks_the_token_used() {
        when(tokenRepository.findByTokenHash(PasswordResetTokens.hash("raw")))
                .thenReturn(Optional.of(tokenFor("raw", Duration.ofMinutes(30))));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        useCase.execute("raw", "BrandNew123");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getSessionVersion()).isEqualTo(2);
        verify(userRepository).save(user);
        verify(tokenRepository).save(argThat((PasswordResetToken t) -> t.getUsedAt() != null));
    }

    @Test
    void a_used_token_fails_with_the_generic_error() {
        PasswordResetToken used = tokenFor("raw", Duration.ofMinutes(30));
        used.markUsed(Instant.now());
        when(tokenRepository.findByTokenHash(PasswordResetTokens.hash("raw"))).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> useCase.execute("raw", "BrandNew123"))
                .isInstanceOf(DomainException.class)
                .hasMessage("El enlace no es válido o ya expiró");
    }

    @Test
    void an_expired_token_fails_with_the_same_error() {
        when(tokenRepository.findByTokenHash(PasswordResetTokens.hash("raw")))
                .thenReturn(Optional.of(tokenFor("raw", Duration.ofMinutes(-1))));

        assertThatThrownBy(() -> useCase.execute("raw", "BrandNew123"))
                .isInstanceOf(DomainException.class)
                .hasMessage("El enlace no es válido o ya expiró");
    }

    @Test
    void an_unknown_token_fails_with_the_same_error() {
        when(tokenRepository.findByTokenHash(PasswordResetTokens.hash("nope"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("nope", "BrandNew123"))
                .isInstanceOf(DomainException.class)
                .hasMessage("El enlace no es válido o ya expiró");
    }

    @Test
    void a_blank_token_fails_with_the_same_error() {
        assertThatThrownBy(() -> useCase.execute("   ", "BrandNew123"))
                .isInstanceOf(DomainException.class)
                .hasMessage("El enlace no es válido o ya expiró");
    }

    @Test
    void a_password_under_8_characters_is_rejected_before_the_token_is_even_looked_up() {
        assertThatThrownBy(() -> useCase.execute("raw", "short"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("al menos 8");
    }
}
