package com.pilarestilo.payment.application.usecases;

import com.pilarestilo.payment.application.dto.PaymentDto;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListPaymentsUseCase {

    private final PaymentRepository paymentRepository;

    public ListPaymentsUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public Page<PaymentDto> execute(PaymentStatus status, Pageable pageable) {
        if (status != null) {
            return paymentRepository.findByStatus(status, pageable)
                    .map(RegisterPaymentUseCase::toDto);
        }
        return paymentRepository.findAll(pageable).map(RegisterPaymentUseCase::toDto);
    }
}
