package com.pilarestilo.shared.auth.infrastructure.web;

import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.application.dto.UserProfileDto;
import com.pilarestilo.shared.auth.application.usecases.*;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import org.springframework.web.multipart.MultipartFile;
import com.pilarestilo.shared.auth.infrastructure.web.requests.ChangeMyPasswordRequest;
import com.pilarestilo.shared.auth.infrastructure.web.requests.GoogleLoginRequest;
import com.pilarestilo.shared.auth.infrastructure.web.requests.LoginRequest;
import com.pilarestilo.shared.auth.infrastructure.web.requests.RefreshRequest;
import com.pilarestilo.shared.auth.infrastructure.web.requests.RegisterRequest;
import com.pilarestilo.shared.auth.infrastructure.web.requests.UpdateMyProfileRequest;
import jakarta.servlet.http.HttpServletRequest;
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
    private final GetMyProfileUseCase getMyProfileUseCase;
    private final UpdateMyProfileUseCase updateMyProfileUseCase;
    private final ChangeMyPasswordUseCase changeMyPasswordUseCase;
    private final GoogleLoginUseCase googleLoginUseCase;
    private final UploadMyAvatarUseCase uploadMyAvatarUseCase;

    // One dependency per use case this controller wires -- exactly what a hexagonal web adapter
    // is for, not a design smell to fix by folding use cases together.
    @SuppressWarnings("java:S107")
    public AuthController(RegisterUseCase registerUseCase,
                          LoginUseCase loginUseCase,
                          RefreshTokenUseCase refreshTokenUseCase,
                          GetMyProfileUseCase getMyProfileUseCase,
                          UpdateMyProfileUseCase updateMyProfileUseCase,
                          ChangeMyPasswordUseCase changeMyPasswordUseCase,
                          GoogleLoginUseCase googleLoginUseCase,
                          UploadMyAvatarUseCase uploadMyAvatarUseCase) {
        this.registerUseCase = registerUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.getMyProfileUseCase = getMyProfileUseCase;
        this.updateMyProfileUseCase = updateMyProfileUseCase;
        this.changeMyPasswordUseCase = changeMyPasswordUseCase;
        this.googleLoginUseCase = googleLoginUseCase;
        this.uploadMyAvatarUseCase = uploadMyAvatarUseCase;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthTokenDto register(@RequestBody @Valid RegisterRequest req,
                                 HttpServletRequest httpRequest) {
        return registerUseCase.execute(
                req.email(), req.password(), req.fullName(),
                callerAddress(httpRequest),
                httpRequest.getHeader("User-Agent"),
                req.acceptsMarketing());
    }

    /**
     * The address the acceptance came from. Caddy sits in front, so the socket address is Caddy;
     * X-Forwarded-For carries the original, and only its first entry is worth anything.
     */
    private static String callerAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/login")
    public AuthTokenDto login(@RequestBody @Valid LoginRequest req) {
        return loginUseCase.execute(req.email(), req.password());
    }

    @PostMapping("/refresh")
    public AuthTokenDto refresh(@RequestBody @Valid RefreshRequest req) {
        return refreshTokenUseCase.execute(req.refreshToken());
    }

    @PostMapping("/google")
    public AuthTokenDto googleLogin(@RequestBody @Valid GoogleLoginRequest req) {
        return googleLoginUseCase.execute(req.idToken());
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
        return updateMyProfileUseCase.execute(
                currentUser.id(),
                request.fullName(),
                request.phone(),
                request.notificationChannelPreference()
        );
    }

    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeMyPassword(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                 @RequestBody @Valid ChangeMyPasswordRequest request) {
        changeMyPasswordUseCase.execute(currentUser.id(), request.currentPassword(), request.newPassword());
    }

    @PutMapping("/me/avatar")
    public java.util.Map<String, String> uploadAvatar(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam("file") MultipartFile file) {
        String url = uploadMyAvatarUseCase.execute(currentUser.id(), file);
        return java.util.Map.of("avatarUrl", url);
    }
}
