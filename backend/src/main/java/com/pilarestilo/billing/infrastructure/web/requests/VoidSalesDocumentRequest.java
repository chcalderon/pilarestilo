package com.pilarestilo.billing.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidSalesDocumentRequest(
        @NotBlank @Size(max = 500) String reason
) {}
