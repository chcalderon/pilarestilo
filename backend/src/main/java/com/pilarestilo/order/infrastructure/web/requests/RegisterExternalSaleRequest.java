package com.pilarestilo.order.infrastructure.web.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RegisterExternalSaleRequest(
        @NotBlank String idempotencyKey,
        @NotBlank @Size(max = 160) String buyerName,
        @NotBlank @Size(max = 160) String buyerContact,
        @NotBlank String salesChannel,   // INSTAGRAM | FACEBOOK | WHATSAPP | MANUAL
        @NotBlank String paymentMethod,  // TRANSFER | OTHER
        @NotBlank String deliveryMethod, // SHIPPING | PICKUP
        @Size(max = 500) String shippingAddress,
        @Size(max = 1000) String notes,
        @NotEmpty @Size(max = 50) @Valid List<Line> items
) {
    public record Line(
            @NotNull UUID productId,
            String variantColor,
            String variantSize,
            @Min(1) @Max(999) int quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice
    ) {}
}
