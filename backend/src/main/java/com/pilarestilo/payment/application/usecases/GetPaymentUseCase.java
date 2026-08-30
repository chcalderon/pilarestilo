package com.pilarestilo.payment.application.usecases;

import com.pilarestilo.payment.application.dto.PaymentDto;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class GetPaymentUseCase {

    private final PaymentRepository paymentRepository;

    public GetPaymentUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public PaymentDto execute(UUID id) {
        return paymentRepository.findById(id)
                .map(RegisterPaymentUseCase::toDto)
                .orElseThrow(() -> new NoSuchElementException("Payment not found: " + id));
    }
}
