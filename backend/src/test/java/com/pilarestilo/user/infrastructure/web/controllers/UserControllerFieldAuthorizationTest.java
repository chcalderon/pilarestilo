package com.pilarestilo.user.infrastructure.web.controllers;

import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.application.dto.UserDto;
import com.pilarestilo.user.application.usecases.AdminResetUserPasswordUseCase;
import com.pilarestilo.user.application.usecases.CreateUserUseCase;
import com.pilarestilo.user.application.usecases.DeleteUserUseCase;
import com.pilarestilo.user.application.usecases.GetUserUseCase;
import com.pilarestilo.user.application.usecases.ListUsersUseCase;
import com.pilarestilo.user.application.usecases.SearchUsersUseCase;
import com.pilarestilo.user.application.usecases.UpdateUserUseCase;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.infrastructure.web.requests.UpdateUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerFieldAuthorizationTest {

    @Test
    void users_update_cannot_change_role_without_legacy_admin() {
        UpdateUserUseCase updateUserUseCase = mock(UpdateUserUseCase.class);
        UserController controller = controller(updateUserUseCase);
        AuthenticatedUser actor = new AuthenticatedUser(
                UUID.randomUUID(),
                "administracion@pilarestilo.com",
                UserRole.ADMINISTRACION,
                List.of("usuarios"),
                List.of("users.read", "users.update")
        );

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> controller.update(
                UUID.randomUUID(),
                new UpdateUserRequest("Nuevo nombre", "SELLER", true),
                actor,
                new UsernamePasswordAuthenticationToken(actor, null, actor.toAuthorities())
        ));

        assertEquals("Role changes require admin legacy access", ex.getMessage());
    }

    @Test
    void users_update_allows_safe_field_changes() {
        UpdateUserUseCase updateUserUseCase = mock(UpdateUserUseCase.class);
        UserController controller = controller(updateUserUseCase);
        UUID targetUserId = UUID.randomUUID();
        AuthenticatedUser actor = new AuthenticatedUser(
                UUID.randomUUID(),
                "administracion@pilarestilo.com",
                UserRole.ADMINISTRACION,
                List.of("usuarios"),
                List.of("users.read", "users.update")
        );
        UserDto expected = new UserDto(
                targetUserId,
                "cliente@test.com",
                "Nuevo nombre",
                null,
                "CUSTOMER",
                true,
                Instant.now()
        );
        when(updateUserUseCase.execute(eq(targetUserId), eq("Nuevo nombre"), eq(null), eq(true))).thenReturn(expected);

        UserDto result = controller.update(
                targetUserId,
                new UpdateUserRequest("Nuevo nombre", null, true),
                actor,
                new UsernamePasswordAuthenticationToken(actor, null, actor.toAuthorities())
        );

        assertEquals(expected, result);
        verify(updateUserUseCase).execute(targetUserId, "Nuevo nombre", null, true);
    }

    @Test
    void admin_cannot_block_self() {
        UpdateUserUseCase updateUserUseCase = mock(UpdateUserUseCase.class);
        UserController controller = controller(updateUserUseCase);
        UUID actorId = UUID.randomUUID();
        AuthenticatedUser actor = new AuthenticatedUser(
                actorId,
                "admin@pilarestilo.com",
                UserRole.ADMIN,
                List.of("usuarios"),
                List.of("users.read", "users.update", "users.assign_role")
        );

        DomainException ex = assertThrows(DomainException.class, () -> controller.update(
                actorId,
                new UpdateUserRequest("Admin", null, false),
                actor,
                new UsernamePasswordAuthenticationToken(actor, null, actor.toAuthorities())
        ));

        assertEquals("You cannot block your own account", ex.getMessage());
    }

    private UserController controller(UpdateUserUseCase updateUserUseCase) {
        return new UserController(
                mock(CreateUserUseCase.class),
                mock(GetUserUseCase.class),
                mock(ListUsersUseCase.class),
                updateUserUseCase,
                mock(DeleteUserUseCase.class),
                mock(AdminResetUserPasswordUseCase.class),
                mock(SearchUsersUseCase.class)
        );
    }
}
