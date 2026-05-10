package com.pilarestilo.order.infrastructure.web.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull(message = "customerId is required")
        UUID customerId,

        @NotNull @NotEmpty(message = "Order must have at least one item")
        @Valid List<OrderItemRequest> items,

        @NotNull(message = "paymentMethod is required")
        String paymentMethod,

        @NotBlank(message = "shippingZoneCode is required")
        String shippingZoneCode,

        @NotBlank(message = "shippingCourierId is required")
        String shippingCourierId,

        String shippingAddressReference,

        String notes,

        String discountCode
) {}
