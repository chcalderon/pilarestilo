package com.pilarestilo.notificationservice.domain.ports;

import com.pilarestilo.notificationservice.domain.view.ReturnView;

import java.util.Optional;
import java.util.UUID;

public interface ReturnReadPort {
    Optional<ReturnView> findById(UUID returnId);
}
