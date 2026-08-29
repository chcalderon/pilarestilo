package com.pilarestilo.user.application.usecases;

import com.pilarestilo.shared.auth.domain.ports.PasswordEncoder;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminResetUserPasswordUseCaseTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    AdminResetUserPasswordUseCase useCase;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new AdminResetUserPasswordUseCase(userRepository, passwordEncoder);
    }

    @Test
    void it_changes_the_hash_and_bumps_the_session_version() {
        User worker = User.reconstruct(userId, "seller@pilarestilo.com", "Seller", UserRole.SELLER, true, "old", Instant.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(worker));
        when(passwordEncoder.encode("temporal123")).thenReturn("new-hash");

        useCase.execute(userId, "temporal123");

        assertThat(worker.getPasswordHash()).isEqualTo("new-hash");
        assertThat(worker.getSessionVersion()).isEqualTo(2);
        verify(userRepository).save(worker);
    }
}
