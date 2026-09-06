package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.domain.model.PasswordResetToken;
import com.pilarestilo.shared.auth.domain.ports.PasswordResetMailer;
import com.pilarestilo.shared.auth.domain.ports.PasswordResetTokenRepository;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestPasswordResetUseCaseTest {

    @Mock UserRepository userRepository;
    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock PasswordResetMailer mailer;

    RequestPasswordResetUseCase useCase;

    private User user;

    @BeforeEach
    void setUp() {
        useCase = new RequestPasswordResetUseCase(userRepository, tokenRepository, mailer, 30);
        user = User.reconstruct(
                java.util.UUID.randomUUID(), "camila@example.com", "Camila", UserRole.CUSTOMER, true, "hash",
                java.time.Instant.now());
    }

    @Test
    void an_existing_email_invalidates_prior_tokens_saves_a_hash_and_sends_a_6_digit_code() {
        when(userRepository.findByEmail("camila@example.com")).thenReturn(Optional.of(user));

        useCase.execute("  Camila@Example.com  ");

        verify(tokenRepository).invalidateUnusedForUser(user.getId());
        verify(tokenRepository).save(argThat((PasswordResetToken t) ->
                t.getUserId().equals(user.getId()) && t.getTokenHash().length() == 64));
        verify(mailer).sendResetCode(eq("camila@example.com"), eq("Camila"),
                argThat((String c) -> c != null && c.matches("\\d{6}")));
    }

    @Test
    void an_unknown_email_creates_and_sends_nothing() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        useCase.execute("ghost@nowhere.invalid");

        verifyNoInteractions(tokenRepository, mailer);
    }

    @Test
    void a_blank_email_is_a_no_op() {
        useCase.execute("   ");

        verifyNoInteractions(userRepository, tokenRepository, mailer);
    }

    @Test
    void a_dead_smtp_host_does_not_blow_up_the_request() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("smtp down")).when(mailer).sendResetCode(any(), any(), any());

        assertThatCode(() -> useCase.execute("camila@example.com")).doesNotThrowAnyException();
    }
}
