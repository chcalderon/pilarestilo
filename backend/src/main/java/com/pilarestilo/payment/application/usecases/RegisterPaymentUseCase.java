package com.pilarestilo.payment.application.usecases;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.application.dto.PaymentDto;
import com.pilarestilo.payment.domain.events.PaymentRegistered;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RegisterPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final DomainEventPublisher eventPublisher;

    public RegisterPaymentUseCase(PaymentRepository paymentRepository,
                                   DomainEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PaymentDto execute(UUID orderId, PaymentMethod method) {
        Payment payment = Payment.create(orderId, method);
        Payment saved = paymentRepository.save(payment);
        eventPublisher.publish(new PaymentRegistered(saved.getId(), saved.getOrderId(), Instant.now()));
        return toDto(saved);
    }

    static PaymentDto toDto(Payment p) {
        return new PaymentDto(
                p.getId(), p.getOrderId(), p.getMethod(), p.getStatus(),
                p.getProofReference(), p.getReviewedBy(), p.getReviewedAt(), p.getCreatedAt()
        );
    }
}
