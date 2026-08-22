package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.privacy.application.usecases.RecordConsentUseCase;
import com.pilarestilo.shared.application.AfterCommitPublisher;
import com.pilarestilo.shared.auth.domain.ports.PasswordEncoder;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.events.UserRegistered;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUseCaseTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RecordConsentUseCase recordConsentUseCase;
    @Mock AfterCommitPublisher afterCommitPublisher;

    RegisterUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterUseCase(
                userRepository, passwordEncoder, jwtTokenProvider, recordConsentUseCase, afterCommitPublisher);

        when(userRepository.existsByEmail("camila@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hash");
        lenient().when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(jwtTokenProvider.generateAccessToken(
                        any(), any(), any(UserRole.class), anyList(), anyList()))
                .thenReturn("access-token");
        lenient().when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh-token");
    }

    /** A new account should tell the customer it exists, same as an order does. */
    @Test
    void publishesUserRegisteredAfterCreatingTheAccount() {
        useCase.execute("camila@example.com", "secret123", "Camila Torres", "127.0.0.1", "Mozilla");

        verify(afterCommitPublisher).publish(argThat(event ->
                event instanceof UserRegistered registered && registered.userId() != null));
    }
}
