package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GetCurrentUserUseCase {

    public Optional<AuthenticatedUser> execute() {
        // There is no authentication at all on the public endpoints, and this threw rather than
        // answering "nobody" — the one answer it exists to give.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }
}
