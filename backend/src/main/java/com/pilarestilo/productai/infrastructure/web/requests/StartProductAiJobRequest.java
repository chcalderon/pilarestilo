package com.pilarestilo.productai.infrastructure.web.requests;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartProductAiJobRequest(
        @NotNull(message = "draftId is required")
        UUID draftId,
        Integer targetWidth,
        Integer targetHeight,
        Boolean strictGarmentFidelity,
        Boolean forbidTextLogoWatermark
) {
}
