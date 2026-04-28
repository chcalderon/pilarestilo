package com.pilarestilo.dispatch.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record MarkDispatchedRequest(
        @NotBlank String carrier,
        @NotBlank String trackingCode,
        LocalDate scheduledDate,
        String notes
) {}
