package com.pilarestilo.shared.auth.infrastructure.web;

import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.application.dto.UserProfileDto;
import com.pilarestilo.shared.auth.application.usecases.*;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.shared.auth.infrastructure.web.requests.ChangeMyPasswordRequest;
import com.pilarestilo.shared.auth.infrastructure.web.requests.LoginRequest;
import com.pilarestilo.shared.auth.infrastructure.web.requests.RefreshRequest;
import com.pilarestilo.shared.auth.infrastructure.web.requests.RegisterRequest;
import com.pilarestilo.shared.auth.infrastructure.web.requests.UpdateMyProfileRequest;
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
    private final GetMyProfileUseCase getMyProfileUseCase;
    private final UpdateMyProfileUseCase updateMyProfileUseCase;
    private final ChangeMyPasswordUseCase changeMyPasswordUseCase;

    public AuthController(RegisterUseCase registerUseCase,
                          LoginUseCase loginUseCase,
                          RefreshTokenUseCase refreshTokenUseCase,
                          GetCurrentUserUseCase getCurrentUserUseCase,
                          GetMyProfileUseCase getMyProfileUseCase,
                          UpdateMyProfileUseCase updateMyProfileUseCase,
                          ChangeMyPasswordUseCase changeMyPasswordUseCase) {
        this.registerUseCase = registerUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.getMyProfileUseCase = getMyProfileUseCase;
        this.updateMyProfileUseCase = updateMyProfileUseCase;
        this.changeMyPasswordUseCase = changeMyPasswordUseCase;
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

    @GetMapping("/me/profile")
    public UserProfileDto myProfile(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return getMyProfileUseCase.execute(currentUser.id());
    }

    @PatchMapping("/me/profile")
    public UserProfileDto updateMyProfile(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                          @RequestBody @Valid UpdateMyProfileRequest request) {
        return updateMyProfileUseCase.execute(currentUser.id(), request.fullName(), request.phone());
    }

    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeMyPassword(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                 @RequestBody @Valid ChangeMyPasswordRequest request) {
        changeMyPasswordUseCase.execute(currentUser.id(), request.currentPassword(), request.newPassword());
    }
}
