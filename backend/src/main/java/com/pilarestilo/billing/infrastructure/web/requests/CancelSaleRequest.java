package com.pilarestilo.billing.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelSaleRequest(
        /** Recorded on the voided document, so a cancelled sale can be accounted for a year later. */
        @NotBlank @Size(max = 500) String reason
) {}
