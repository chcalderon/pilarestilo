package com.pilarestilo.payment.application.usecases;

import com.pilarestilo.payment.application.dto.PaymentDto;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ReviewPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final DomainEventPublisher eventPublisher;

    public ReviewPaymentUseCase(PaymentRepository paymentRepository,
                                 DomainEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PaymentDto approve(UUID paymentId, UUID reviewerId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));
        if (payment.getStatus().name().equals("SUBMITTED")) {
            payment.markUnderReview();
        }
        payment.approve(reviewerId);
        Payment saved = paymentRepository.save(payment);
        eventPublisher.publish(new PaymentConfirmed(saved.getId(), saved.getOrderId(), Instant.now()));
        return RegisterPaymentUseCase.toDto(saved);
    }

    @Transactional
    public PaymentDto reject(UUID paymentId, UUID reviewerId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));
        if (payment.getStatus().name().equals("SUBMITTED")) {
            payment.markUnderReview();
        }
        payment.reject(reviewerId);
        Payment saved = paymentRepository.save(payment);
        eventPublisher.publish(new PaymentRejected(saved.getId(), saved.getOrderId(), reviewerId, Instant.now()));
        return RegisterPaymentUseCase.toDto(saved);
    }
}
