package com.pilarestilo.customeraddress.application.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerAddressDto(
        UUID id,
        UUID customerId,
        String label,
        String recipientName,
        String phone,
        String line1,
        String line2,
        Integer regionId,
        Long cityId,
        Long communeId,
        String comuna,
        String city,
        String region,
        String reference,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
}
