package com.pilarestilo.payment.domain.ports;

import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.shared.application.Money;

import java.util.UUID;

public interface PaymentGatewayPort {

    String initiatePayment(UUID orderId, Money amount);

    PaymentStatus checkStatus(String gatewayReference);
}
