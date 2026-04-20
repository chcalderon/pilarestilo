package com.pilarestilo.shared.auth.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;

public record UpdateMyProfileRequest(
        @NotBlank(message = "Full name is required")
        String fullName
) {}
