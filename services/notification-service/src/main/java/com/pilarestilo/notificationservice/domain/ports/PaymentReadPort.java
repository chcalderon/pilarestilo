package com.pilarestilo.notificationservice.domain.ports;

import com.pilarestilo.notificationservice.domain.view.PaymentView;

import java.util.Optional;
import java.util.UUID;

public interface PaymentReadPort {
    Optional<PaymentView> findById(UUID paymentId);
}
