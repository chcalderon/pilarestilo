package com.pilarestilo.notificationservice.domain.ports;

import com.pilarestilo.notificationservice.domain.view.CustomerView;

import java.util.Optional;
import java.util.UUID;

public interface CustomerReadPort {
    Optional<CustomerView> findById(UUID userId);
}
