package com.pilarestilo.user.infrastructure.web.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Role is required")
        String role,

        String passwordHash
) {}
