package com.pilarestilo.shared.auth.infrastructure.web.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Email String email,

        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "El código tiene 6 dígitos")
        String code,

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "New password must have at least 8 characters")
        String newPassword
) {}
