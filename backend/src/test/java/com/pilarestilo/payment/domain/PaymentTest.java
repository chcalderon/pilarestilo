package com.pilarestilo.payment.domain;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {

    private static final String TRANSFER_HOLDER = "Pilar Estilo";
    private static final String TRANSFER_EMAIL = "pagos@pilarestilo.com";
    private static final String TRANSFER_ACCOUNT = "1234567890";
    private static final String TRANSFER_BANK = "Banco de Chile";
    private static final String TRANSFER_TYPE = "Cuenta Corriente";

    private Payment newPendingPayment() {
        return Payment.create(
                UUID.randomUUID(),
                PaymentMethod.BANK_TRANSFER,
                TRANSFER_HOLDER,
                TRANSFER_EMAIL,
                TRANSFER_ACCOUNT,
                TRANSFER_BANK,
                TRANSFER_TYPE
        );
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

    @Test
    void gateway_confirm_from_pending_marks_approved() {
        Payment p = Payment.create(UUID.randomUUID(), PaymentMethod.PAYMENT_GATEWAY);
        boolean changed = p.confirmByGateway();

        assertTrue(changed);
        assertEquals(PaymentStatus.APPROVED, p.getStatus());
        assertNull(p.getReviewedBy());
        assertNotNull(p.getReviewedAt());
    }

    @Test
    void gateway_reject_from_pending_marks_rejected() {
        Payment p = Payment.create(UUID.randomUUID(), PaymentMethod.PAYMENT_GATEWAY);
        boolean changed = p.rejectByGateway();

        assertTrue(changed);
        assertEquals(PaymentStatus.REJECTED, p.getStatus());
        assertNull(p.getReviewedBy());
        assertNotNull(p.getReviewedAt());
    }

    @Test
    void gateway_confirm_is_idempotent_when_already_approved() {
        Payment p = Payment.create(UUID.randomUUID(), PaymentMethod.PAYMENT_GATEWAY);
        p.confirmByGateway();

        boolean changed = p.confirmByGateway();

        assertFalse(changed);
        assertEquals(PaymentStatus.APPROVED, p.getStatus());
    }

    @Test
    void gateway_reject_after_approved_throws() {
        Payment p = Payment.create(UUID.randomUUID(), PaymentMethod.PAYMENT_GATEWAY);
        p.confirmByGateway();

        assertThrows(DomainException.class, p::rejectByGateway);
    }

    @Test
    void systemCancel_sets_REJECTED_with_reason_and_system_reviewer() {
        Payment p = newPendingPayment();
        p.systemCancel("Cierre por sistema");

        assertEquals(PaymentStatus.REJECTED, p.getStatus());
        assertEquals(Payment.SYSTEM_REVIEWER_ID, p.getReviewedBy());
        assertNotNull(p.getReviewedAt());
        assertEquals("Cierre por sistema", p.getRejectionReason());
    }

    @Test
    void systemCancel_rejects_non_PENDING_payment() {
        Payment p = newPendingPayment();
        p.submitProof("ref-001");

        assertThrows(DomainException.class, () -> p.systemCancel("reason"));
    }

    @Test
    void systemCancel_rejects_non_BANK_TRANSFER_payment() {
        Payment p = Payment.create(UUID.randomUUID(), PaymentMethod.PAYMENT_GATEWAY);

        assertThrows(DomainException.class, () -> p.systemCancel("reason"));
    }
}
