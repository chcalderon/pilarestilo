package com.pilarestilo.paymentservice.web;

import com.pilarestilo.paymentservice.persistence.PaymentEntity;
import com.pilarestilo.paymentservice.web.dto.PaymentDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentMapperTest {

    @Test
    void maps_entity_to_dto() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID reviewedBy = UUID.randomUUID();
        Instant now = Instant.now();
        PaymentEntity entity = new PaymentEntity();
        entity.setId(paymentId);
        entity.setOrderId(orderId);
        entity.setMethod("BANK_TRANSFER");
        entity.setStatus("PENDING");
        entity.setProofReference("proof");
        entity.setTransferAccountHolderName("Pilar Estilo");
        entity.setTransferAccountEmail("admin@pilarestilo.com");
        entity.setTransferAccountNumber("123");
        entity.setTransferAccountType("Corriente");
        entity.setReviewedBy(reviewedBy);
        entity.setReviewedAt(now);
        entity.setCreatedAt(now);

        PaymentDto dto = PaymentMapper.toDto(entity);

        assertEquals(paymentId, dto.id());
        assertEquals(orderId, dto.orderId());
        assertEquals("BANK_TRANSFER", dto.method());
        assertEquals("PENDING", dto.status());
        assertEquals("proof", dto.proofReference());
        assertEquals("Pilar Estilo", dto.transferAccountHolderName());
        assertEquals("admin@pilarestilo.com", dto.transferAccountEmail());
        assertEquals("123", dto.transferAccountNumber());
        assertEquals("Corriente", dto.transferAccountType());
        assertEquals(reviewedBy, dto.reviewedBy());
        assertEquals(now, dto.reviewedAt());
        assertEquals(now, dto.createdAt());
    }
}
