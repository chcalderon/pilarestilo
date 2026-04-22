package com.pilarestilo.payment.application.usecases;

import com.pilarestilo.payment.application.dto.PaymentDto;
import com.pilarestilo.payment.application.remote.PaymentRemoteQueryClient;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class GetPaymentByOrderUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentRemoteQueryClient paymentRemoteQueryClient;

    public GetPaymentByOrderUseCase(PaymentRepository paymentRepository,
                                    PaymentRemoteQueryClient paymentRemoteQueryClient) {
        this.paymentRepository = paymentRepository;
        this.paymentRemoteQueryClient = paymentRemoteQueryClient;
    }

    public PaymentDto execute(UUID orderId) {
        if (paymentRemoteQueryClient.isEnabled()) {
            return paymentRemoteQueryClient.getByOrderId(orderId)
                    .orElseThrow(() -> new NoSuchElementException("Payment not found for order: " + orderId));
        }
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found for order: " + orderId));
        return RegisterPaymentUseCase.toDto(payment);
    }
}
