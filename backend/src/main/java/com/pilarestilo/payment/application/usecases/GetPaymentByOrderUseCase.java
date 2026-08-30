package com.pilarestilo.payment.application.usecases;

import com.pilarestilo.payment.application.dto.PaymentDto;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class GetPaymentByOrderUseCase {

    private final PaymentRepository paymentRepository;

    public GetPaymentByOrderUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public PaymentDto execute(UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found for order: " + orderId));
        return RegisterPaymentUseCase.toDto(payment);
    }
}
