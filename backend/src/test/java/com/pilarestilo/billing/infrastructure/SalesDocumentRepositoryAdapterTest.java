package com.pilarestilo.billing.infrastructure;

import com.pilarestilo.billing.domain.enums.SalesDocumentStatus;
import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.infrastructure.persistence.entities.SalesDocumentEntity;
import com.pilarestilo.billing.infrastructure.persistence.repositories.SalesDocumentJpaRepository;
import com.pilarestilo.billing.infrastructure.persistence.repositories.SalesDocumentRepositoryAdapter;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.application.TaxBreakdown;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A tax document is kept for six years, so the mapping is the part that has to be exact: the
 * amounts, the folio, and the snapshot of who bought — the row outlives the user record it copied.
 */
@ExtendWith(MockitoExtension.class)
class SalesDocumentRepositoryAdapterTest {

    @Mock SalesDocumentJpaRepository jpaRepository;

    SalesDocumentRepositoryAdapter adapter;

    private final UUID orderId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new SalesDocumentRepositoryAdapter(jpaRepository);
    }

    private SalesDocument boleta() {
        return SalesDocument.issue(
                orderId, SalesDocumentType.BOLETA, "1042",
                TaxBreakdown.fromGross(Money.of(new BigDecimal("45990")), new BigDecimal("19.00")),
                "11.111.111-1", null, null, "Ana Perez", "ana@correo.cl", "boleta.pdf", actor, null);
    }

    private SalesDocument factura() {
        return SalesDocument.issue(
                orderId, SalesDocumentType.FACTURA, "500",
                TaxBreakdown.fromGross(Money.of(new BigDecimal("45990")), new BigDecimal("19.00")),
                "76.123.456-7", "Comercial Ana Perez Ltda.", "Venta de ropa",
                "Ana Perez", "ana@correo.cl", "factura.pdf", actor, null);
    }

    @Test
    void a_document_survives_the_round_trip_whole() {
        SalesDocument original = boleta();
        when(jpaRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        SalesDocument saved = adapter.save(original);

        assertEquals("1042", saved.getFolio());
        assertEquals(SalesDocumentType.BOLETA, saved.getType());
        assertEquals(SalesDocumentStatus.ISSUED, saved.getStatus());
        assertEquals(original.getNetAmount().amount(), saved.getNetAmount().amount());
        assertEquals(original.getTaxAmount().amount(), saved.getTaxAmount().amount());
        assertEquals(new BigDecimal("19.00"), saved.getTaxRate());
        assertEquals("CLP", saved.getTotalAmount().currency());
        // The snapshot: the buyer may be anonymised long before the document may be destroyed.
        assertEquals("Ana Perez", saved.getReceiverName());
        assertEquals("ana@correo.cl", saved.getReceiverEmail());
        assertEquals("11.111.111-1", saved.getReceiverRut());
        assertEquals("boleta.pdf", saved.getFileUrl());
        assertEquals(actor, saved.getIssuedBy());
    }

    @Test
    void a_facturas_razon_social_and_giro_survive_the_round_trip() {
        SalesDocument original = factura();
        when(jpaRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        SalesDocument saved = adapter.save(original);

        assertEquals("Comercial Ana Perez Ltda.", saved.getReceiverBusinessName());
        assertEquals("Venta de ropa", saved.getReceiverBusinessActivity());
    }

    @Test
    void voiding_carries_its_reason_and_its_author() {
        SalesDocument document = boleta();
        document.voidDocument("Folio equivocado", actor);
        when(jpaRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        SalesDocument saved = adapter.save(document);

        assertEquals(SalesDocumentStatus.VOIDED, saved.getStatus());
        assertEquals("Folio equivocado", saved.getVoidReason());
        assertEquals(actor, saved.getVoidedBy());
        assertNotNull(saved.getVoidedAt());
    }

    /**
     * The live document of a sale excludes credit notes: one lives alongside the boleta it undoes,
     * so a read that included it would find two rows and throw.
     */
    @Test
    void the_live_document_query_leaves_credit_notes_out() {
        when(jpaRepository.findByOrderIdAndStatusNotAndDocumentTypeNot(
                eq(orderId), eq("VOIDED"), eq("NOTA_CREDITO"))).thenReturn(Optional.empty());

        assertTrue(adapter.findLiveByOrderId(orderId).isEmpty());

        verify(jpaRepository).findByOrderIdAndStatusNotAndDocumentTypeNot(orderId, "VOIDED", "NOTA_CREDITO");
    }

    @Test
    void credit_notes_standing_against_a_document_are_the_only_ones_counted() {
        SalesDocument note = SalesDocument.creditNote(
                orderId, "87",
                TaxBreakdown.fromGross(Money.of(new BigDecimal("10000")), new BigDecimal("19.00")),
                SalesDocument.REFERENCE_CORRECTS_AMOUNT, boleta(), null, actor);
        SalesDocument reissuedBoleta = boleta();
        when(jpaRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        adapter.save(note);
        adapter.save(reissuedBoleta);
        ArgumentCaptor<SalesDocumentEntity> captor = ArgumentCaptor.forClass(SalesDocumentEntity.class);
        verify(jpaRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<SalesDocumentEntity> rows = captor.getAllValues();

        when(jpaRepository.findByReplacesDocumentIdAndStatusNot(any(), eq("VOIDED"))).thenReturn(rows);

        List<SalesDocument> live = adapter.findLiveCreditNotesFor(UUID.randomUUID());

        // Both rows come back from the query; only the note is a credit note.
        assertEquals(1, live.size());
        assertEquals(SalesDocumentType.NOTA_CREDITO, live.get(0).getType());
        assertEquals(SalesDocument.REFERENCE_CORRECTS_AMOUNT, live.get(0).getReferenceCode());
    }

    @Test
    void the_orphan_sweep_reads_every_stored_file_name() {
        when(jpaRepository.findAllFileUrls()).thenReturn(List.of("a.pdf", "b.jpg"));

        assertEquals(2, adapter.findAllStoredFileNames().size());
    }

    @Test
    void a_folio_is_looked_up_by_type_as_well() {
        when(jpaRepository.existsByDocumentTypeAndFolio("BOLETA", "1042")).thenReturn(true);

        assertTrue(adapter.existsByTypeAndFolio(SalesDocumentType.BOLETA, "1042"));
    }
}
