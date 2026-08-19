package com.pilarestilo.privacy.infrastructure.web.requests;

import jakarta.validation.constraints.Size;

/** Asking to be forgotten. The reason is optional: it is a right, not a request. */
public record RequestDeletionRequest(
        @Size(max = 500) String reason
) {}
