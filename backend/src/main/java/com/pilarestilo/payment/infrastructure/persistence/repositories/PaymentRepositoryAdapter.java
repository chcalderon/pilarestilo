package com.pilarestilo.payment.infrastructure.persistence.repositories;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.payment.infrastructure.persistence.entities.PaymentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    public PaymentRepositoryAdapter(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = toEntity(payment);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Payment> findByOrderId(UUID orderId) {
        return jpaRepository.findByOrderId(orderId).map(this::toDomain);
    }

    @Override
    public Page<Payment> findByStatus(PaymentStatus status, Pageable pageable) {
        return jpaRepository.findByStatus(status, pageable).map(this::toDomain);
    }

    @Override
    public Page<Payment> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public List<Payment> findPendingBankTransfersOlderThan(Instant cutoff, int limit) {
        return jpaRepository.findPendingBankTransfersOlderThan(
                PaymentStatus.PENDING, PaymentMethod.TRANSFER, cutoff, limit
        ).stream().map(this::toDomain).toList();
    }

    private PaymentEntity toEntity(Payment p) {
        PaymentEntity e = new PaymentEntity();
        e.setId(p.getId());
        e.setOrderId(p.getOrderId());
        e.setMethod(p.getMethod());
        e.setStatus(p.getStatus());
        e.setProofReference(p.getProofReference());
        e.setTransferAccountHolderName(p.getTransferAccountHolderName());
        e.setTransferAccountEmail(p.getTransferAccountEmail());
        e.setTransferAccountNumber(p.getTransferAccountNumber());
        e.setTransferBankName(p.getTransferBankName());
        e.setTransferAccountType(p.getTransferAccountType());
        e.setReviewedBy(p.getReviewedBy());
        e.setReviewedAt(p.getReviewedAt());
        e.setCreatedAt(p.getCreatedAt());
        e.setRejectionReason(p.getRejectionReason());
        return e;
    }

    private Payment toDomain(PaymentEntity e) {
        return Payment.reconstruct(
                e.getId(), e.getOrderId(), e.getMethod(), e.getStatus(),
                e.getProofReference(),
                e.getTransferAccountHolderName(),
                e.getTransferAccountEmail(),
                e.getTransferAccountNumber(),
                e.getTransferBankName(),
                e.getTransferAccountType(),
                e.getReviewedBy(), e.getReviewedAt(), e.getCreatedAt(),
                e.getRejectionReason()
        );
    }
}
