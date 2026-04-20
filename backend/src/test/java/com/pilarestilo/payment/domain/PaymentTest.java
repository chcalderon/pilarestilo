package com.pilarestilo.payment.domain;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {

    private Payment newPendingPayment() {
        return Payment.create(UUID.randomUUID(), PaymentMethod.BANK_TRANSFER);
    }

    @Test
    void new_payment_has_status_PENDING() {
        assertEquals(PaymentStatus.PENDING, newPendingPayment().getStatus());
    }

    @Test
    void can_submit_proof_from_PENDING() {
        Payment p = newPendingPayment();
        p.submitProof("ref-001");
        assertEquals(PaymentStatus.SUBMITTED, p.getStatus());
        assertEquals("ref-001", p.getProofReference());
    }

    @Test
    void cannot_approve_from_SUBMITTED_without_review() {
        Payment p = newPendingPayment();
        p.submitProof("ref-001");
        assertThrows(DomainException.class, () -> p.approve(UUID.randomUUID()));
    }

    @Test
    void full_approval_flow() {
        Payment p = newPendingPayment();
        p.submitProof("ref-001");
        p.markUnderReview();
        UUID reviewer = UUID.randomUUID();
        p.approve(reviewer);
        assertEquals(PaymentStatus.APPROVED, p.getStatus());
        assertEquals(reviewer, p.getReviewedBy());
        assertNotNull(p.getReviewedAt());
    }

    @Test
    void full_rejection_flow() {
        Payment p = newPendingPayment();
        p.submitProof("ref-001");
        p.markUnderReview();
        UUID reviewer = UUID.randomUUID();
        p.reject(reviewer);
        assertEquals(PaymentStatus.REJECTED, p.getStatus());
        assertEquals(reviewer, p.getReviewedBy());
    }

    @Test
    void cannot_submit_proof_from_approved_status() {
        Payment p = newPendingPayment();
        p.submitProof("ref-001");
        p.markUnderReview();
        p.approve(UUID.randomUUID());
        assertThrows(DomainException.class, () -> p.submitProof("ref-002"));
    }

    @Test
    void cannot_mark_under_review_from_pending() {
        Payment p = newPendingPayment();
        assertThrows(DomainException.class, p::markUnderReview);
    }
}
