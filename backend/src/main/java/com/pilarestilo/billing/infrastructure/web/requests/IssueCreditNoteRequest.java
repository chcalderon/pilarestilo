package com.pilarestilo.billing.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record IssueCreditNoteRequest(
        @NotNull UUID orderId,
        /** The folio the SII gave, typed by hand like the boleta's. */
        @NotBlank @Size(max = 40) String folio,
        /** What the note credits. The whole document total annuls the sale; less corrects it. */
        @NotNull @Positive BigDecimal amount,
        /** Path returned by the document upload endpoint. */
        @Size(max = 500) String fileUrl,
        /** The return this note settles, when it comes from one. */
        UUID returnId
) {}
