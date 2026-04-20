package com.pilarestilo.payment.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;

public record SubmitProofRequest(
        @NotBlank(message = "proofReference is required")
        String proofReference
) {}
