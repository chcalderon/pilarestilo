package com.pilarestilo.payment.application.usecases;

import com.pilarestilo.payment.application.dto.PaymentDto;
import com.pilarestilo.payment.domain.events.PaymentSubmitted;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class SubmitPaymentProofUseCase {

    private final PaymentRepository paymentRepository;
    private final DomainEventPublisher eventPublisher;

    public SubmitPaymentProofUseCase(PaymentRepository paymentRepository,
                                      DomainEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PaymentDto execute(UUID paymentId, String proofReference) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));
        payment.submitProof(proofReference);
        Payment saved = paymentRepository.save(payment);
        eventPublisher.publish(new PaymentSubmitted(saved.getId(), saved.getProofReference(), Instant.now()));
        return RegisterPaymentUseCase.toDto(saved);
    }
}
