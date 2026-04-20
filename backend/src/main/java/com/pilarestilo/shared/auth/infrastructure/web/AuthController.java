package com.pilarestilo.shared.auth.infrastructure.web;

import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.application.usecases.*;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.shared.auth.infrastructure.web.requests.LoginRequest;
import com.pilarestilo.shared.auth.infrastructure.web.requests.RefreshRequest;
import com.pilarestilo.shared.auth.infrastructure.web.requests.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;

    public AuthController(RegisterUseCase registerUseCase,
                          LoginUseCase loginUseCase,
                          RefreshTokenUseCase refreshTokenUseCase,
                          GetCurrentUserUseCase getCurrentUserUseCase) {
        this.registerUseCase = registerUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthTokenDto register(@RequestBody @Valid RegisterRequest req) {
        return registerUseCase.execute(req.email(), req.password(), req.fullName());
    }

    @PostMapping("/login")
    public AuthTokenDto login(@RequestBody @Valid LoginRequest req) {
        return loginUseCase.execute(req.email(), req.password());
    }

    @PostMapping("/refresh")
    public AuthTokenDto refresh(@RequestBody @Valid RefreshRequest req) {
        return refreshTokenUseCase.execute(req.refreshToken());
    }

    @GetMapping("/me")
    public AuthenticatedUser me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return currentUser;
    }
}
