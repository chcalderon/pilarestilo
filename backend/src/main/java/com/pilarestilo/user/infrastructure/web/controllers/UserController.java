package com.pilarestilo.user.infrastructure.web.controllers;

import com.pilarestilo.user.application.dto.UserDto;
import com.pilarestilo.user.application.dto.UserSearchResultDto;
import com.pilarestilo.user.application.usecases.CreateUserUseCase;
import com.pilarestilo.user.application.usecases.AdminResetUserPasswordUseCase;
import com.pilarestilo.user.application.usecases.DeleteUserUseCase;
import com.pilarestilo.user.application.usecases.GetUserUseCase;
import com.pilarestilo.user.application.usecases.ListUsersUseCase;
import com.pilarestilo.user.application.usecases.SearchUsersUseCase;
import com.pilarestilo.user.application.usecases.UpdateUserUseCase;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.rbac.domain.PermissionRegistry;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.infrastructure.web.requests.CreateUserRequest;
import com.pilarestilo.user.infrastructure.web.requests.ResetUserPasswordRequest;
import com.pilarestilo.user.infrastructure.web.requests.UpdateUserRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final CreateUserUseCase createUserUseCase;
    private final GetUserUseCase getUserUseCase;
    private final ListUsersUseCase listUsersUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeleteUserUseCase deleteUserUseCase;
    private final AdminResetUserPasswordUseCase adminResetUserPasswordUseCase;
    private final SearchUsersUseCase searchUsersUseCase;

    public UserController(CreateUserUseCase createUserUseCase,
                           GetUserUseCase getUserUseCase,
                           ListUsersUseCase listUsersUseCase,
                           UpdateUserUseCase updateUserUseCase,
                           DeleteUserUseCase deleteUserUseCase,
                           AdminResetUserPasswordUseCase adminResetUserPasswordUseCase,
                           SearchUsersUseCase searchUsersUseCase) {
        this.createUserUseCase = createUserUseCase;
        this.getUserUseCase = getUserUseCase;
        this.listUsersUseCase = listUsersUseCase;
        this.updateUserUseCase = updateUserUseCase;
        this.deleteUserUseCase = deleteUserUseCase;
        this.adminResetUserPasswordUseCase = adminResetUserPasswordUseCase;
        this.searchUsersUseCase = searchUsersUseCase;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest request) {
        UserDto dto = createUserUseCase.execute(
                request.email(), request.fullName(), request.role(), request.passwordHash()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).USERS_READ)")
    public Page<UserDto> list(@RequestParam(required = false) String role,
                              @RequestParam(required = false) Boolean active,
                              Pageable pageable) {
        return listUsersUseCase.execute(pageable, role, active);
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
    public List<UserSearchResultDto> search(@RequestParam String q) {
        return searchUsersUseCase.execute(q);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).USERS_READ)")
    public UserDto getById(@PathVariable UUID id) {
        return getUserUseCase.execute(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).USERS_UPDATE)")
    public UserDto update(@PathVariable UUID id,
                          @RequestBody UpdateUserRequest request,
                          @AuthenticationPrincipal AuthenticatedUser currentUser,
                          Authentication authentication) {
        assertRoleChangeAllowed(id, request, currentUser);
        assertNotSelfDemoting(id, request, currentUser);
        return updateUserUseCase.execute(id, request.fullName(), request.role(), request.active());
    }

    private void assertRoleChangeAllowed(UUID id, UpdateUserRequest request, AuthenticatedUser currentUser) {
        boolean isLegacyAdmin = currentUser != null && currentUser.role() == UserRole.ADMIN;
        if (request.role() == null || isLegacyAdmin) {
            return;
        }
        if (log.isWarnEnabled()) {
            log.warn("[RBAC] denied role mutation userId={} actor={} requiredPermission={}",
                    id,
                    currentUser == null ? "anonymous" : currentUser.email(),
                    PermissionRegistry.USERS_ASSIGN_ROLE.code());
        }
        throw new AccessDeniedException("Role changes require admin legacy access");
    }

    /** An admin editing their own account cannot lock themselves out. */
    private void assertNotSelfDemoting(UUID id, UpdateUserRequest request, AuthenticatedUser currentUser) {
        if (currentUser == null || !currentUser.id().equals(id)) {
            return;
        }
        if (request.active() != null && !request.active()) {
            throw new DomainException("You cannot block your own account");
        }
        if (request.role() != null && !"ADMIN".equalsIgnoreCase(request.role())) {
            throw new DomainException("You cannot remove your own admin role");
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id,
                       @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser != null && currentUser.id().equals(id)) {
            throw new DomainException("You cannot delete your own account");
        }
        deleteUserUseCase.execute(id);
    }

    @PatchMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void resetPassword(@PathVariable UUID id,
                              @RequestBody @Valid ResetUserPasswordRequest request) {
        adminResetUserPasswordUseCase.execute(id, request.newPassword());
    }
}
