package com.pilarestilo.billing.application;

import com.pilarestilo.billing.application.dto.SalesDocumentDto;
import com.pilarestilo.billing.application.usecases.IssueCreditNoteUseCase;
import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.events.SalesDocumentIssued;
import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.returns.domain.enums.ReturnKind;
import com.pilarestilo.returns.domain.model.ReturnRequest;
import com.pilarestilo.returns.domain.ports.ReturnRequestRepository;
import com.pilarestilo.shared.application.AfterCommitPublisher;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.application.TaxBreakdown;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rules a credit note has that a boleta does not: it needs something to act upon, it says what
 * it does to it, and the sum of what stands against a document can never exceed the document.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IssueCreditNoteUseCaseTest {

    @Mock SalesDocumentRepository salesDocumentRepository;
    @Mock ReturnRequestRepository returnRequestRepository;
    @Mock AfterCommitPublisher eventPublisher;

    IssueCreditNoteUseCase useCase;

    private final UUID orderId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private SalesDocument boleta;

    @BeforeEach
    void setUp() {
        useCase = new IssueCreditNoteUseCase(
                salesDocumentRepository, returnRequestRepository, eventPublisher);

        boleta = SalesDocument.issue(
                orderId,
                SalesDocumentType.BOLETA,
                "1042",
                TaxBreakdown.fromGross(Money.of(new BigDecimal("45990")), new BigDecimal("19.00")),
                null, null, null, "Ana Perez", "ana@correo.cl", null, actor, null);

        when(salesDocumentRepository.findLiveByOrderId(orderId)).thenReturn(Optional.of(boleta));
        when(salesDocumentRepository.findLiveCreditNotesFor(boleta.getId())).thenReturn(List.of());
        when(salesDocumentRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void crediting_the_whole_total_annuls_the_sale() {
        SalesDocumentDto note = useCase.execute(
                orderId, "87", new BigDecimal("45990"), null, null, actor);

        assertEquals(SalesDocumentType.NOTA_CREDITO.name(), note.documentType());
        assertEquals(SalesDocument.REFERENCE_ANNULS, note.referenceCode());
        assertEquals(boleta.getId(), note.replacesDocumentId());
    }

    /** A partial refund corrects an amount; calling that an annulment would misdeclare it. */
    @Test
    void crediting_part_of_it_corrects_the_amount() {
        SalesDocumentDto note = useCase.execute(
                orderId, "88", new BigDecimal("20000"), null, null, actor);

        assertEquals(SalesDocument.REFERENCE_CORRECTS_AMOUNT, note.referenceCode());
    }

    /** The VAT of the note comes from the rate the boleta was declared under, not today's. */
    @Test
    void the_note_carries_the_referenced_documents_rate() {
        SalesDocumentDto note = useCase.execute(
                orderId, "89", new BigDecimal("45990"), null, null, actor);

        assertEquals(new BigDecimal("19.00"), note.taxRate());
        assertEquals(note.totalAmount(), note.netAmount().add(note.taxAmount()));
    }

    @Test
    void a_sale_with_no_live_document_has_nothing_to_credit() {
        when(salesDocumentRepository.findLiveByOrderId(orderId)).thenReturn(Optional.empty());

        DomainException error = assertThrows(DomainException.class, () -> useCase.execute(
                orderId, "90", new BigDecimal("45990"), null, null, actor));

        assertTrue(error.getMessage().contains("nothing to undo"));
    }

    /** Two partial notes may not add up to more than the sale they undo. */
    @Test
    void credit_notes_cannot_exceed_what_the_document_declared() {
        SalesDocument first = SalesDocument.creditNote(
                orderId, "87",
                TaxBreakdown.fromGross(Money.of(new BigDecimal("40000")), new BigDecimal("19.00")),
                SalesDocument.REFERENCE_CORRECTS_AMOUNT, boleta, null, actor);
        when(salesDocumentRepository.findLiveCreditNotesFor(boleta.getId())).thenReturn(List.of(first));

        DomainException error = assertThrows(DomainException.class, () -> useCase.execute(
                orderId, "91", new BigDecimal("10000"), null, null, actor));

        assertTrue(error.getMessage().contains("left to credit"));
        verify(salesDocumentRepository, never()).save(any());
    }

    @Test
    void a_folio_is_never_registered_twice() {
        when(salesDocumentRepository.existsByTypeAndFolio(SalesDocumentType.NOTA_CREDITO, "87"))
                .thenReturn(true);

        assertThrows(DomainException.class, () -> useCase.execute(
                orderId, "87", new BigDecimal("45990"), null, null, actor));
    }

    @Test
    void the_note_is_bound_to_the_return_it_settles() {
        ReturnRequest request = ReturnRequest.open(orderId, ReturnKind.RETRACTO, "No me quedó", null);
        when(returnRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        SalesDocumentDto note = useCase.execute(
                orderId, "92", new BigDecimal("45990"), null, request.getId(), actor);

        assertEquals(note.id(), request.getCreditNoteId());
        verify(returnRequestRepository).save(request);
    }

    @Test
    void a_return_from_another_sale_is_refused() {
        ReturnRequest other = ReturnRequest.open(UUID.randomUUID(), ReturnKind.DEVOLUCION, "otra", null);
        when(returnRequestRepository.findById(other.getId())).thenReturn(Optional.of(other));

        DomainException error = assertThrows(DomainException.class, () -> useCase.execute(
                orderId, "93", new BigDecimal("45990"), null, other.getId(), actor));

        assertTrue(error.getMessage().contains("different sale"));
    }

    /** Announced after the commit, like every other document: the consumer reads the row back. */
    @Test
    void the_customer_is_told_once_it_is_recorded() {
        useCase.execute(orderId, "94", new BigDecimal("45990"), null, null, actor);

        verify(eventPublisher).publish(any(SalesDocumentIssued.class));
    }
}
