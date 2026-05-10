package com.pilarestilo.dispatch.application.dto;

import com.pilarestilo.dispatch.domain.model.Dispatch;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DispatchDto(
        UUID id, UUID orderId, UUID dispatcherId, String status,
        String carrier, String trackingCode, LocalDate scheduledDate,
        LocalDateTime dispatchedAt, LocalDateTime deliveredAt,
        String notes, LocalDateTime createdAt,
        String orderShippingZoneCode,
        String orderShippingCourierId,
        String orderShippingCourierName,
        String orderShippingAddressReference,
        String carrierOverrideConfigured,
        String carrierOverrideSelected,
        UUID carrierOverrideBy,
        LocalDateTime carrierOverrideAt
) {
    public static DispatchDto from(Dispatch d) {
        return new DispatchDto(d.getId(), d.getOrderId(), d.getDispatcherId(), d.getStatus().name(),
                d.getCarrier(), d.getTrackingCode(), d.getScheduledDate(),
                d.getDispatchedAt(), d.getDeliveredAt(), d.getNotes(), d.getCreatedAt(),
                d.getOrderShippingZoneCode(),
                d.getOrderShippingCourierId(),
                d.getOrderShippingCourierName(),
                d.getOrderShippingAddressReference(),
                d.getCarrierOverrideConfigured(),
                d.getCarrierOverrideSelected(),
                d.getCarrierOverrideBy(),
                d.getCarrierOverrideAt());
    }

    public DispatchDto withOrderShipping(
            String shippingZoneCode,
            String shippingCourierId,
            String shippingCourierName,
            String shippingAddressReference
    ) {
        return new DispatchDto(
                id,
                orderId,
                dispatcherId,
                status,
                carrier,
                trackingCode,
                scheduledDate,
                dispatchedAt,
                deliveredAt,
                notes,
                createdAt,
                shippingZoneCode,
                shippingCourierId,
                shippingCourierName,
                shippingAddressReference,
                carrierOverrideConfigured,
                carrierOverrideSelected,
                carrierOverrideBy,
                carrierOverrideAt
        );
    }
}
