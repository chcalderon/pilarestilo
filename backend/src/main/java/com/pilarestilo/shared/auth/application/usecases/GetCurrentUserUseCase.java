package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GetCurrentUserUseCase {

    public Optional<AuthenticatedUser> execute() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }
}
