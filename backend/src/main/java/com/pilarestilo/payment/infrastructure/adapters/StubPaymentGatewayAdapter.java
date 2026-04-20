package com.pilarestilo.payment.infrastructure.adapters;

import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.domain.ports.PaymentGatewayPort;
import com.pilarestilo.shared.application.Money;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StubPaymentGatewayAdapter implements PaymentGatewayPort {

    @Override
    public String initiatePayment(UUID orderId, Money amount) {
        throw new UnsupportedOperationException("Payment gateway integration pending");
    }

    @Override
    public PaymentStatus checkStatus(String gatewayReference) {
        throw new UnsupportedOperationException("Payment gateway integration pending");
    }
}
