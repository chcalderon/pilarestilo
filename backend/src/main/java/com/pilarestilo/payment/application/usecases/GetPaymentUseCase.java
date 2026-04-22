package com.pilarestilo.payment.application.usecases;

import com.pilarestilo.payment.application.dto.PaymentDto;
import com.pilarestilo.payment.application.remote.PaymentRemoteQueryClient;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class GetPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentRemoteQueryClient paymentRemoteQueryClient;

    public GetPaymentUseCase(PaymentRepository paymentRepository,
                             PaymentRemoteQueryClient paymentRemoteQueryClient) {
        this.paymentRepository = paymentRepository;
        this.paymentRemoteQueryClient = paymentRemoteQueryClient;
    }

    @Transactional(readOnly = true)
    public PaymentDto execute(UUID id) {
        if (paymentRemoteQueryClient.isEnabled()) {
            return paymentRemoteQueryClient.getById(id)
                    .orElseThrow(() -> new NoSuchElementException("Payment not found: " + id));
        }
        return paymentRepository.findById(id)
                .map(RegisterPaymentUseCase::toDto)
                .orElseThrow(() -> new NoSuchElementException("Payment not found: " + id));
    }
}
