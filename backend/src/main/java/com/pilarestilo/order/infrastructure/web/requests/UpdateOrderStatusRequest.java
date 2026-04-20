package com.pilarestilo.order.infrastructure.web.requests;

import com.pilarestilo.order.domain.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull(message = "status is required")
        OrderStatus status
) {}
