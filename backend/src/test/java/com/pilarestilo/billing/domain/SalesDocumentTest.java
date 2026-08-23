package com.pilarestilo.billing.domain;

import com.pilarestilo.billing.domain.enums.SalesDocumentStatus;
import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.application.TaxBreakdown;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalesDocumentTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    @Test
    void an_issued_document_is_live_and_carries_its_amounts() {
        SalesDocument document = boleta("1042");

        assertTrue(document.isLive());
        assertEquals(SalesDocumentStatus.ISSUED, document.getStatus());
        assertEquals(0, document.getNetAmount().amount().compareTo(new BigDecimal("38647")));
        assertEquals(0, document.getTaxAmount().amount().compareTo(new BigDecimal("7343")));
        assertEquals(0, document.getTotalAmount().amount().compareTo(new BigDecimal("45990")));
    }

    @Test
    void a_boleta_does_not_need_the_receiver_rut() {
        SalesDocument document = boleta("1042");

        assertNull(document.getReceiverRut());
    }

    @Test
    void a_factura_does_need_the_receiver_rut() {
        TaxBreakdown amounts = amounts();

        DomainException ex = assertThrows(DomainException.class, () -> SalesDocument.issue(
                ORDER_ID, SalesDocumentType.FACTURA, "500", amounts,
                null, "Comercial Ana Perez Ltda.", "Venta de ropa", "Ana Perez", "ana@correo.cl", null, ACTOR, null));

        assertTrue(ex.getMessage().contains("factura"));
    }

    @Test
    void a_factura_does_need_the_receiver_business_name() {
        TaxBreakdown amounts = amounts();

        DomainException ex = assertThrows(DomainException.class, () -> SalesDocument.issue(
                ORDER_ID, SalesDocumentType.FACTURA, "500", amounts,
                "76.123.456-7", null, "Venta de ropa", "Ana Perez", "ana@correo.cl", null, ACTOR, null));

        assertTrue(ex.getMessage().contains("razon social"));
    }

    @Test
    void a_factura_does_need_the_receiver_business_activity() {
        TaxBreakdown amounts = amounts();

        DomainException ex = assertThrows(DomainException.class, () -> SalesDocument.issue(
                ORDER_ID, SalesDocumentType.FACTURA, "500", amounts,
                "76.123.456-7", "Comercial Ana Perez Ltda.", null, "Ana Perez", "ana@correo.cl", null, ACTOR, null));

        assertTrue(ex.getMessage().contains("giro"));
    }

    @Test
    void a_factura_with_full_receiver_data_is_issued() {
        SalesDocument document = SalesDocument.issue(
                ORDER_ID, SalesDocumentType.FACTURA, "500", amounts(),
                "76.123.456-7", "Comercial Ana Perez Ltda.", "Venta de ropa", "Ana Perez", "ana@correo.cl", null, ACTOR, null);

        assertEquals("76.123.456-7", document.getReceiverRut());
        assertEquals("Comercial Ana Perez Ltda.", document.getReceiverBusinessName());
        assertEquals("Venta de ropa", document.getReceiverBusinessActivity());
    }

    @Test
    void blank_fields_are_stored_as_absent_rather_than_as_empty_strings() {
        SalesDocument document = SalesDocument.issue(
                ORDER_ID, SalesDocumentType.BOLETA, "1042", amounts(),
                "   ", "  ", "  ", "  ", "  ", "  ", ACTOR, null);

        assertNull(document.getReceiverRut());
        assertNull(document.getReceiverBusinessName());
        assertNull(document.getReceiverBusinessActivity());
        assertNull(document.getReceiverName());
        assertNull(document.getReceiverEmail());
        assertNull(document.getFileUrl());
    }

    @Test
    void requires_a_folio_and_an_issuer() {
        TaxBreakdown amounts = amounts();

        assertThrows(DomainException.class, () -> SalesDocument.issue(
                ORDER_ID, SalesDocumentType.BOLETA, "  ", amounts,
                null, null, null, null, null, null, ACTOR, null));
        assertThrows(DomainException.class, () -> SalesDocument.issue(
                ORDER_ID, SalesDocumentType.BOLETA, "1042", amounts,
                null, null, null, null, null, null, null, null));
    }

    @Test
    void voiding_records_who_and_why() {
        SalesDocument document = boleta("1042");

        document.voidDocument("Folio equivocado", ACTOR);

        assertEquals(SalesDocumentStatus.VOIDED, document.getStatus());
        assertEquals("Folio equivocado", document.getVoidReason());
        assertEquals(ACTOR, document.getVoidedBy());
        assertTrue(document.getVoidedAt() != null);
    }

    /** A void nobody can account for a year later is the reason the reason is mandatory. */
    @Test
    void voiding_without_a_reason_is_refused() {
        SalesDocument document = boleta("1042");

        assertThrows(DomainException.class, () -> document.voidDocument("  ", ACTOR));
        assertThrows(DomainException.class, () -> document.voidDocument("Folio equivocado", null));
    }

    @Test
    void a_document_cannot_be_voided_twice() {
        SalesDocument document = boleta("1042");
        document.voidDocument("Folio equivocado", ACTOR);

        assertThrows(DomainException.class, () -> document.voidDocument("De nuevo", ACTOR));
    }

    private SalesDocument boleta(String folio) {
        return SalesDocument.issue(ORDER_ID, SalesDocumentType.BOLETA, folio, amounts(),
                null, null, null, "Ana Perez", "ana@correo.cl", null, ACTOR, null);
    }

    private TaxBreakdown amounts() {
        return TaxBreakdown.fromGross(Money.of(new BigDecimal("45990")), new BigDecimal("19.00"));
    }
}
