package com.pilarestilo.privacy.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Refusing a right requires saying why; the customer is owed the reason. */
public record RefuseDeletionRequest(
        @NotBlank @Size(max = 500) String reason
) {}
