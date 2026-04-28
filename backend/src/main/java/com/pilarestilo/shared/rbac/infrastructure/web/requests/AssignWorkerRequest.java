package com.pilarestilo.shared.rbac.infrastructure.web.requests;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AssignWorkerRequest(
        @NotNull String role,
        LocalDate vigencyStart,
        LocalDate vigencyEnd
) {}
