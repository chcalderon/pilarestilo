package com.pilarestilo.shared.auth.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String refreshToken) {}
