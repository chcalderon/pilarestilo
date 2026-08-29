package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.rbac.application.RolePermissionResolutionService;
import com.pilarestilo.shared.rbac.domain.model.ResolvedPermissions;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseSvTest {

    private static final String SECRET = "U2VjcmV0U2VjcmV0MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=";

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(SECRET);

    @Mock UserRepository userRepository;
    @Mock RolePermissionResolutionService rolePermissionResolutionService;

    RefreshTokenUseCase useCase;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new RefreshTokenUseCase(jwtTokenProvider, userRepository, rolePermissionResolutionService);
        lenient().when(rolePermissionResolutionService.resolve(any())).thenReturn(ResolvedPermissions.empty());
    }

    private User userAtSessionVersion(int sv) {
        User user = User.reconstruct(userId, "ana@correo.cl", "Ana", UserRole.CUSTOMER, true, "hash", Instant.now());
        user.setSessionVersion(sv);
        return user;
    }

    @Test
    void a_refresh_token_from_before_a_reset_is_rejected() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userAtSessionVersion(3)));
        String stale = jwtTokenProvider.generateRefreshToken(userId, 2);

        assertThatThrownBy(() -> useCase.execute(stale))
                .isInstanceOf(DomainException.class)
                .hasMessage("Session no longer valid");
    }

    @Test
    void a_refresh_token_whose_session_version_matches_still_works() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userAtSessionVersion(3)));
        String current = jwtTokenProvider.generateRefreshToken(userId, 3);

        assertThat(useCase.execute(current).accessToken()).isNotBlank();
    }
}
