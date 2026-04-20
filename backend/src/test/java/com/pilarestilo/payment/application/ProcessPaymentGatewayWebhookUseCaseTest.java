package com.pilarestilo.payment.application;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.application.usecases.ProcessPaymentGatewayWebhookUseCase;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessPaymentGatewayWebhookUseCaseTest {

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    DomainEventPublisher eventPublisher;

    @InjectMocks
    ProcessPaymentGatewayWebhookUseCase useCase;

    @Test
    void approves_gateway_payment_and_publishes_payment_confirmed() {
        Payment payment = Payment.create(UUID.randomUUID(), PaymentMethod.PAYMENT_GATEWAY);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(payment.getId(), "APPROVED");

        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
        verify(paymentRepository).save(payment);
        verify(eventPublisher).publish(any(PaymentConfirmed.class));
    }

    @Test
    void repeated_approved_webhook_is_idempotent() {
        Payment payment = Payment.create(UUID.randomUUID(), PaymentMethod.PAYMENT_GATEWAY);
        payment.confirmByGateway();
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        useCase.execute(payment.getId(), "APPROVED");

        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(PaymentConfirmed.class));
    }

    @Test
    void rejects_gateway_payment_and_publishes_payment_rejected() {
        Payment payment = Payment.create(UUID.randomUUID(), PaymentMethod.PAYMENT_GATEWAY);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(payment.getId(), "FAILED");

        assertEquals(PaymentStatus.REJECTED, payment.getStatus());
        verify(paymentRepository).save(payment);
        verify(eventPublisher).publish(any(PaymentRejected.class));
    }

    @Test
    void webhook_for_non_gateway_payment_throws() {
        Payment payment = Payment.create(UUID.randomUUID(), PaymentMethod.BANK_TRANSFER);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThrows(DomainException.class, () -> useCase.execute(payment.getId(), "APPROVED"));
    }

    @Test
    void unknown_gateway_status_throws() {
        Payment payment = Payment.create(UUID.randomUUID(), PaymentMethod.PAYMENT_GATEWAY);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThrows(DomainException.class, () -> useCase.execute(payment.getId(), "WHATEVER_STATUS"));
    }
}

