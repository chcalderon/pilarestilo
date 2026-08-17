package com.pilarestilo.billing.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record IssueSalesDocumentRequest(
        @NotNull UUID orderId,
        /** BOLETA or FACTURA. Absent means BOLETA, which is what the shop issues today. */
        @Size(max = 20) String documentType,
        @NotBlank @Size(max = 40) String folio,
        /** Optional for a boleta by law; validated only in shape, and stored only when sent. */
        @Size(max = 20) String receiverRut,
        /** Path returned by the document upload endpoint. */
        @Size(max = 500) String fileUrl
) {}
