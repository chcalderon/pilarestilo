package com.pilarestilo.shared.auth.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
