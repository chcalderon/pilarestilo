package com.pilarestilo.user.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetUserPasswordRequest(
        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "New password must have at least 8 characters")
        String newPassword
) {}
