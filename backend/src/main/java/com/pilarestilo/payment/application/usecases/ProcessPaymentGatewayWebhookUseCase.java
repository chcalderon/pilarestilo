package com.pilarestilo.payment.application.usecases;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ProcessPaymentGatewayWebhookUseCase {

    private final PaymentRepository paymentRepository;
    private final DomainEventPublisher eventPublisher;

    public ProcessPaymentGatewayWebhookUseCase(PaymentRepository paymentRepository,
                                               DomainEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void execute(UUID paymentId, String gatewayStatus) {
        var payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));

        if (payment.getMethod() != PaymentMethod.WEBPAY && payment.getMethod() != PaymentMethod.MERCADOPAGO) {
            throw new DomainException("Webhook received for non-gateway payment");
        }

        PaymentStatus resolvedStatus = resolveStatus(gatewayStatus);
        switch (resolvedStatus) {
            case APPROVED -> {
                boolean changed = payment.confirmByGateway();
                if (!changed) return;
                var saved = paymentRepository.save(payment);
                eventPublisher.publish(new PaymentConfirmed(saved.getId(), saved.getOrderId(), Instant.now()));
            }
            case REJECTED -> {
                boolean changed = payment.rejectByGateway();
                if (!changed) return;
                var saved = paymentRepository.save(payment);
                eventPublisher.publish(new PaymentRejected(saved.getId(), saved.getOrderId(), null, Instant.now()));
            }
            default -> {
                // Non-final webhook events are accepted but do not change state.
            }
        }
    }

    @Transactional
    public void executeByOrderId(UUID orderId, String gatewayStatus) {
        var payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found for order: " + orderId));
        execute(payment.getId(), gatewayStatus);
    }

    private PaymentStatus resolveStatus(String gatewayStatus) {
        if (gatewayStatus == null || gatewayStatus.isBlank()) {
            throw new DomainException("gatewayStatus is required");
        }

        String normalized = gatewayStatus.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVED", "PAID", "SUCCEEDED", "SUCCESS" -> PaymentStatus.APPROVED;
            case "REJECTED", "FAILED", "CANCELLED", "VOIDED" -> PaymentStatus.REJECTED;
            case "PENDING", "IN_PROCESS", "PROCESSING", "REQUIRES_ACTION", "UNDER_REVIEW", "SUBMITTED" -> PaymentStatus.PENDING;
            default -> throw new DomainException("Unknown gateway status: " + gatewayStatus);
        };
    }
}
