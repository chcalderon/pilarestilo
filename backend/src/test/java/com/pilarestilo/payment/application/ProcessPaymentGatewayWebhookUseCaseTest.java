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

import java.util.NoSuchElementException;
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

    private static final String TRANSFER_HOLDER = "Pilar Estilo";
    private static final String TRANSFER_EMAIL = "pagos@pilarestilo.com";
    private static final String TRANSFER_ACCOUNT = "1234567890";
    private static final String TRANSFER_BANK = "Banco de Chile";
    private static final String TRANSFER_TYPE = "Cuenta Corriente";

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
        Payment payment = Payment.create(
                UUID.randomUUID(),
                PaymentMethod.BANK_TRANSFER,
                TRANSFER_HOLDER,
                TRANSFER_EMAIL,
                TRANSFER_ACCOUNT,
                TRANSFER_BANK,
                TRANSFER_TYPE
        );
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThrows(DomainException.class, () -> useCase.execute(payment.getId(), "APPROVED"));
    }

    @Test
    void unknown_gateway_status_throws() {
        Payment payment = Payment.create(UUID.randomUUID(), PaymentMethod.PAYMENT_GATEWAY);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThrows(DomainException.class, () -> useCase.execute(payment.getId(), "WHATEVER_STATUS"));
    }

    @Test
    void execute_by_order_id_resolves_payment_and_confirms() {
        UUID orderId = UUID.randomUUID();
        Payment payment = Payment.create(orderId, PaymentMethod.PAYMENT_GATEWAY);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.executeByOrderId(orderId, "APPROVED");

        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
        verify(eventPublisher).publish(any(PaymentConfirmed.class));
    }

    @Test
    void execute_by_order_id_throws_when_order_has_no_payment() {
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> useCase.executeByOrderId(orderId, "APPROVED"));
    }
}
