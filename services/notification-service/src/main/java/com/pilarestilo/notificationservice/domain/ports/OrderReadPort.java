package com.pilarestilo.notificationservice.domain.ports;

import com.pilarestilo.notificationservice.domain.view.OrderView;

import java.util.Optional;
import java.util.UUID;

public interface OrderReadPort {
    Optional<OrderView> findById(UUID orderId);
}
