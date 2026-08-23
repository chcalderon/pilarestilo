package com.pilarestilo.billing.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReissueSalesDocumentRequest(
        @NotBlank @Size(max = 500) String voidReason,
        @Size(max = 20) String documentType,
        @NotBlank @Size(max = 40) String folio,
        @Size(max = 20) String receiverRut,
        @Size(max = 160) String receiverBusinessName,
        @Size(max = 160) String receiverBusinessActivity,
        @Size(max = 500) String fileUrl
) {}
