package com.pilarestilo.publication.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;

public record RescheduleBatchRequest(@NotBlank String scheduledAt) {}
