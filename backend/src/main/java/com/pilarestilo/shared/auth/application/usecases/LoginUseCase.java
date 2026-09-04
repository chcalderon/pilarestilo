package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.domain.ports.PasswordEncoder;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.rbac.application.RolePermissionResolutionService;
import com.pilarestilo.shared.rbac.domain.model.ResolvedPermissions;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RolePermissionResolutionService rolePermissionResolutionService;

    public LoginUseCase(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtTokenProvider jwtTokenProvider,
                        RolePermissionResolutionService rolePermissionResolutionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.rolePermissionResolutionService = rolePermissionResolutionService;
    }

    public AuthTokenDto execute(String email, String rawPassword) {
        /*
         * Stored emails are always lowercase (User.create normalizes on the way in) but nothing
         * normalized the way in on this read side -- a correct password typed under "Maria@..."
         * instead of "maria@..." (mobile auto-capitalize, a pasted signature) missed the match
         * entirely and read back as "Invalid credentials", indistinguishable from a wrong password.
         */
        User user = userRepository.findByEmail(User.normalizeEmail(email))
                .orElseThrow(() -> new DomainException("Invalid credentials"));
        if (!user.isActive()) {
            throw new DomainException("This account is blocked");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new DomainException("Invalid credentials");
        }
        ResolvedPermissions resolvedPermissions = rolePermissionResolutionService.resolve(user.getRole());
        String access  = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                resolvedPermissions.legacyViewKeys(),
                resolvedPermissions.permissionCodes(),
                user.getSessionVersion());
        String refresh = jwtTokenProvider.generateRefreshToken(user.getId(), user.getSessionVersion());
        return AuthTokenDto.of(
                access,
                refresh,
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getFullName(),
                user.getAvatarUrl(),
                resolvedPermissions.legacyViewKeys(),
                resolvedPermissions.permissionCodes());
    }
}
