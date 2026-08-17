package com.pilarestilo.returns.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The request bodies for the returns endpoints, together because each is two or three fields and
 * they are only ever read next to each other.
 */
public final class ReturnRequests {

    private ReturnRequests() {}

    public record Open(
            @NotNull UUID orderId,
            /** RETRACTO or DEVOLUCION. Absent means DEVOLUCION, which is the one the shop opens. */
            @Size(max = 20) String kind,
            @NotBlank @Size(max = 500) String reason
    ) {}

    /** What the customer sends: she never chooses the kind, it is always a retracto. */
    public record OpenRetracto(
            @NotNull UUID orderId,
            @NotBlank @Size(max = 500) String reason
    ) {}

    public record Reject(
            @NotBlank @Size(max = 500) String note
    ) {}

    public record Disposition(
            /** RESTOCKED or DISCARDED. Discarding requires the note. */
            @NotBlank @Size(max = 30) String disposition,
            @Size(max = 500) String note
    ) {}

    public record BankAccount(
            @NotBlank @Size(max = 160) String holder,
            @NotBlank @Size(max = 20) String rut,
            @NotBlank @Size(max = 120) String bankName,
            @NotBlank @Size(max = 80) String accountType,
            @NotBlank @Size(max = 40) String accountNumber
    ) {}

    public record Refund(
            @NotNull @Positive BigDecimal amount,
            @Size(max = 10) String currency,
            @NotBlank @Size(max = 30) String method,
            @NotBlank @Size(max = 200) String reference,
            @Size(max = 500) String fileUrl
    ) {}
}
