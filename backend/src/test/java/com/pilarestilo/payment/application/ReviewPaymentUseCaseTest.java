package com.pilarestilo.payment.application;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.application.usecases.ReviewPaymentUseCase;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewPaymentUseCaseTest {

    private static final String TRANSFER_HOLDER = "Pilar Estilo";
    private static final String TRANSFER_EMAIL = "pagos@pilarestilo.com";
    private static final String TRANSFER_ACCOUNT = "1234567890";
    private static final String TRANSFER_TYPE = "Cuenta Corriente";

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    DomainEventPublisher eventPublisher;

    @InjectMocks
    ReviewPaymentUseCase useCase;

    private Payment paymentReadyForReview() {
        Payment payment = Payment.create(
                UUID.randomUUID(),
                PaymentMethod.BANK_TRANSFER,
                TRANSFER_HOLDER,
                TRANSFER_EMAIL,
                TRANSFER_ACCOUNT,
                TRANSFER_TYPE
        );
        payment.submitProof("ref-123");
        payment.markUnderReview();
        return payment;
    }

    @Test
    void approves_payment_under_review() {
        Payment payment = paymentReadyForReview();
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID reviewerId = UUID.randomUUID();
        useCase.approve(payment.getId(), reviewerId);

        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
        assertEquals(reviewerId, payment.getReviewedBy());
        verify(eventPublisher).publish(any(PaymentConfirmed.class));
    }

    @Test
    void rejects_payment_under_review() {
        Payment payment = paymentReadyForReview();
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID reviewerId = UUID.randomUUID();
        useCase.reject(payment.getId(), reviewerId);

        assertEquals(PaymentStatus.REJECTED, payment.getStatus());
        verify(eventPublisher).publish(any(PaymentRejected.class));
    }

    @Test
    void approve_auto_transitions_from_submitted_to_under_review() {
        Payment payment = Payment.create(
                UUID.randomUUID(),
                PaymentMethod.BANK_TRANSFER,
                TRANSFER_HOLDER,
                TRANSFER_EMAIL,
                TRANSFER_ACCOUNT,
                TRANSFER_TYPE
        );
        payment.submitProof("ref-456");
        // status is SUBMITTED, not UNDER_REVIEW yet
        assertEquals(PaymentStatus.SUBMITTED, payment.getStatus());

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.approve(payment.getId(), UUID.randomUUID());

        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
        verify(eventPublisher).publish(any(PaymentConfirmed.class));
    }

    @Test
    void throws_when_payment_not_found() {
        UUID missingId = UUID.randomUUID();
        when(paymentRepository.findById(missingId)).thenReturn(Optional.empty());
        assertThrows(Exception.class, () -> useCase.approve(missingId, UUID.randomUUID()));
    }
}
