package com.pilarestilo.returns.domain;

import com.pilarestilo.returns.domain.enums.ItemDisposition;
import com.pilarestilo.returns.domain.enums.RefundMethod;
import com.pilarestilo.returns.domain.enums.ReturnKind;
import com.pilarestilo.returns.domain.enums.ReturnStatus;
import com.pilarestilo.returns.domain.model.RefundAccount;
import com.pilarestilo.returns.domain.model.ReturnRequest;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnRequestTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();

    @Test
    void opens_with_the_legal_refund_deadline_already_set() {
        ReturnRequest request = retracto();

        assertEquals(ReturnStatus.REQUESTED, request.getStatus());
        assertEquals(45, Duration.between(request.getRequestedAt(), request.getDeadlineAt()).toDays());
        assertTrue(request.isOpen());
    }

    @Test
    void requires_an_order_a_kind_and_a_reason() {
        assertThrows(DomainException.class,
                () -> ReturnRequest.open(null, ReturnKind.RETRACTO, "Motivo", CUSTOMER));
        assertThrows(DomainException.class,
                () -> ReturnRequest.open(ORDER_ID, null, "Motivo", CUSTOMER));
        assertThrows(DomainException.class,
                () -> ReturnRequest.open(ORDER_ID, ReturnKind.RETRACTO, "  ", CUSTOMER));
    }

    /**
     * The rule the whole law rests on. A retracto opened within its window is a right the customer
     * exercised, not a request the shop weighs — so the refusal is impossible in the aggregate, not
     * merely hidden in the screen.
     */
    @Test
    void a_retracto_cannot_be_refused() {
        ReturnRequest request = retracto();

        DomainException ex = assertThrows(DomainException.class,
                () -> request.reject("No nos conviene"));

        assertTrue(ex.getMessage().contains("right"));
        assertEquals(ReturnStatus.REQUESTED, request.getStatus());
    }

    @Test
    void a_devolucion_can_be_refused_but_only_with_a_reason() {
        ReturnRequest request = devolucion();

        assertThrows(DomainException.class, () -> request.reject("  "));

        request.reject("Fuera de plazo y la prenda viene usada");
        assertEquals(ReturnStatus.REJECTED, request.getStatus());
        assertEquals("Fuera de plazo y la prenda viene usada", request.getResolutionNote());
    }

    /**
     * Receiving the garment puts nothing back on sale. Every returned garment is cleaned, pressed
     * and repaired first, so restocking on arrival would put a dirty garment in the window.
     */
    @Test
    void receiving_the_garment_leaves_it_pending_reconditioning() {
        ReturnRequest request = approvedRetracto();

        request.receive();

        assertEquals(ReturnStatus.RECEIVED, request.getStatus());
        assertEquals(ItemDisposition.PENDING_RECONDITIONING, request.getItemDisposition());
    }

    @Test
    void the_garment_goes_back_on_sale_or_is_discarded_with_a_reason() {
        ReturnRequest restocked = approvedRetracto();
        restocked.receive();
        restocked.resolveDisposition(ItemDisposition.RESTOCKED, null);
        assertEquals(ItemDisposition.RESTOCKED, restocked.getItemDisposition());

        ReturnRequest discarded = approvedRetracto();
        discarded.receive();
        assertThrows(DomainException.class,
                () -> discarded.resolveDisposition(ItemDisposition.DISCARDED, "  "));
        discarded.resolveDisposition(ItemDisposition.DISCARDED, "Mancha irrecuperable");
        assertEquals("Mancha irrecuperable", discarded.getDispositionNote());
    }

    @Test
    void a_disposition_cannot_be_resolved_twice_or_before_the_garment_arrives() {
        ReturnRequest request = approvedRetracto();
        assertThrows(DomainException.class,
                () -> request.resolveDisposition(ItemDisposition.RESTOCKED, null));

        request.receive();
        request.resolveDisposition(ItemDisposition.RESTOCKED, null);
        assertThrows(DomainException.class,
                () -> request.resolveDisposition(ItemDisposition.DISCARDED, "Otra vez"));
    }

    /**
     * The two clocks are independent. The money has forty-five days by law; the workshop takes as
     * long as it takes, and chaining them would let a delay breach a legal deadline.
     */
    @Test
    void the_refund_does_not_wait_for_the_garment() {
        ReturnRequest request = approvedRetracto();

        request.registerRefund(Money.of(new BigDecimal("45990")), RefundMethod.TRANSFERENCIA,
                "OP-99887766", null);

        assertEquals(ReturnStatus.REFUNDED, request.getStatus());
        // Never received, so the garment never entered reconditioning -- and that is fine.
        assertNull(request.getItemDisposition());
    }

    @Test
    void a_refund_needs_an_amount_a_method_and_a_reference() {
        ReturnRequest request = approvedRetracto();
        Money zero = Money.zero();
        Money amount = Money.of(new BigDecimal("100"));

        assertThrows(DomainException.class,
                () -> request.registerRefund(zero, RefundMethod.TRANSFERENCIA, "OP-1", null));
        assertThrows(DomainException.class,
                () -> request.registerRefund(amount, null, "OP-1", null));
        assertThrows(DomainException.class,
                () -> request.registerRefund(amount, RefundMethod.TRANSFERENCIA, "  ", null));
    }

    @Test
    void a_return_that_was_never_approved_cannot_be_refunded() {
        ReturnRequest request = retracto();
        Money amount = Money.of(new BigDecimal("100"));

        assertThrows(DomainException.class,
                () -> request.registerRefund(amount, RefundMethod.TRANSFERENCIA, "OP-1", null));
    }

    /**
     * Once the money has moved the account number has done its work. Holding a customer's bank
     * details for no remaining purpose is only risk, and the Ley 21.719 lands in December.
     */
    @Test
    void the_account_number_is_erased_when_the_refund_settles() {
        ReturnRequest request = approvedRetracto();
        request.attachRefundAccount(RefundAccount.of(
                "Ana Perez", "12.345.678-9", "Banco de Chile", "Cuenta Corriente",
                "cifrado-abc", "6789"));
        assertTrue(request.getRefundAccount().isConfigured());

        request.registerRefund(Money.of(new BigDecimal("45990")), RefundMethod.TRANSFERENCIA,
                "OP-99887766", null);

        assertNull(request.getRefundAccount().numberEncrypted());
        // What identifies the payment afterwards survives.
        assertEquals("6789", request.getRefundAccount().last4());
        assertEquals("Banco de Chile", request.getRefundAccount().bankName());
    }

    @Test
    void bank_details_cannot_be_attached_to_a_closed_return() {
        ReturnRequest request = devolucion();
        request.reject("Fuera de plazo");
        RefundAccount account = RefundAccount.of(
                "Ana", "1-9", "Banco", "Corriente", "cifrado", "1234");

        assertThrows(DomainException.class, () -> request.attachRefundAccount(account));
    }

    @Test
    void days_until_the_deadline_go_negative_once_it_passes() {
        ReturnRequest request = retracto();

        assertTrue(request.daysUntilDeadline(Instant.now()) >= 44);
        assertTrue(request.daysUntilDeadline(Instant.now().plus(Duration.ofDays(50))) < 0);
    }

    private ReturnRequest retracto() {
        return ReturnRequest.open(ORDER_ID, ReturnKind.RETRACTO, "Me arrepenti", CUSTOMER);
    }

    private ReturnRequest devolucion() {
        return ReturnRequest.open(ORDER_ID, ReturnKind.DEVOLUCION, "No le quedo", null);
    }

    private ReturnRequest approvedRetracto() {
        ReturnRequest request = retracto();
        request.approve();
        return request;
    }
}
