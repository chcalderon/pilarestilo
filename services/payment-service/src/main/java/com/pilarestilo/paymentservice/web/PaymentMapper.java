package com.pilarestilo.paymentservice.web;

import com.pilarestilo.paymentservice.persistence.PaymentEntity;
import com.pilarestilo.paymentservice.web.dto.PaymentDto;

public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static PaymentDto toDto(PaymentEntity entity) {
        return new PaymentDto(
                entity.getId(),
                entity.getOrderId(),
                entity.getMethod(),
                entity.getStatus(),
                entity.getProofReference(),
                entity.getTransferAccountHolderName(),
                entity.getTransferAccountEmail(),
                entity.getTransferAccountNumber(),
                entity.getTransferAccountType(),
                entity.getReviewedBy(),
                entity.getReviewedAt(),
                entity.getCreatedAt()
        );
    }
}
