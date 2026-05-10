package com.pilarestilo.shared.auth.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;

public record CreateCustomerAddressRequest(
        @NotBlank(message = "label is required")
        String label,
        @NotBlank(message = "recipientName is required")
        String recipientName,
        @NotBlank(message = "phone is required")
        String phone,
        @NotBlank(message = "line1 is required")
        String line1,
        String line2,
        @NotBlank(message = "comuna is required")
        String comuna,
        @NotBlank(message = "city is required")
        String city,
        @NotBlank(message = "region is required")
        String region,
        String reference,
        Boolean isDefault
) {
}

