package com.pilarestilo.payment.application.usecases;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.application.dto.PaymentDto;
import com.pilarestilo.payment.domain.events.PaymentRegistered;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RegisterPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final DomainEventPublisher eventPublisher;
    private final SystemSettingsRepository systemSettingsRepository;

    public RegisterPaymentUseCase(PaymentRepository paymentRepository,
                                  DomainEventPublisher eventPublisher,
                                  SystemSettingsRepository systemSettingsRepository) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.systemSettingsRepository = systemSettingsRepository;
    }

    @Transactional
    public PaymentDto execute(UUID orderId, PaymentMethod method) {
        Payment payment;
        if (method == PaymentMethod.TRANSFER) {
            var settings = systemSettingsRepository.get();
            payment = Payment.create(
                    orderId,
                    method,
                    settings.getBankTransferAccountHolder(),
                    settings.getBankTransferContactEmail(),
                    settings.getBankTransferAccountNumber(),
                    settings.getBankTransferBankName(),
                    settings.getBankTransferAccountType()
            );
        } else {
            payment = Payment.create(orderId, method);
        }
        Payment saved = paymentRepository.save(payment);
        eventPublisher.publish(new PaymentRegistered(saved.getId(), saved.getOrderId(), Instant.now()));
        return toDto(saved);
    }

    static PaymentDto toDto(Payment p) {
        return new PaymentDto(
                p.getId(), p.getOrderId(), p.getMethod(), p.getStatus(),
                p.getProofReference(),
                p.getTransferAccountHolderName(),
                p.getTransferAccountEmail(),
                p.getTransferAccountNumber(),
                p.getTransferBankName(),
                p.getTransferAccountType(),
                p.getReviewedBy(), p.getReviewedAt(), p.getCreatedAt(),
                p.getRejectionReason(),
                p.getGatewayFlag(), p.getGatewayFlaggedAt()
        );
    }
}
