package com.pilarestilo.shared.auth.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMyProfileRequest(
        @NotBlank(message = "Full name is required")
        String fullName,
        @Size(max = 40, message = "Phone must be at most 40 characters")
        String phone
) {}
