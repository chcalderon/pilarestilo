package com.pilarestilo.user.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.application.dto.UserDto;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateUserUseCaseTest {

    @Mock UserRepository userRepository;

    CreateUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateUserUseCase(userRepository);
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createsAUserWithTheNormalizedEmail() {
        when(userRepository.existsByEmail("nueva@pilarestilo.com")).thenReturn(false);

        UserDto dto = useCase.execute(" Nueva@PilarEstilo.com ", "Nueva Vendedora", "SELLER", "hash");

        assertThat(dto.email()).isEqualTo("nueva@pilarestilo.com");
    }

    /**
     * Same normalize-before-check as registration: an admin retrying under different
     * letter-casing than an existing account must hit this friendly message, not the DB's
     * unique constraint.
     */
    @Test
    void rejectsACreationWhoseEmailOnlyDiffersByCase() {
        when(userRepository.existsByEmail("existente@pilarestilo.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute("Existente@PilarEstilo.com", "Existente", "SELLER", "hash"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("existente@pilarestilo.com");
    }
}
