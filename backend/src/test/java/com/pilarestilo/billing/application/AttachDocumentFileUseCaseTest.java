package com.pilarestilo.billing.application;

import com.pilarestilo.billing.application.usecases.AttachDocumentFileUseCase;
import com.pilarestilo.billing.domain.enums.SalesDocumentStatus;
import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.application.TaxBreakdown;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Filing the paper afterwards, and only that.
 *
 * <p>The shop emits the boleta by hand in the SII app and photographs it, so the picture routinely
 * arrives after the folio has been typed. What this pins is the line between finishing the filing
 * and rewriting it: an empty slot may be filled once, and nothing else about the document moves.
 */
@ExtendWith(MockitoExtension.class)
class AttachDocumentFileUseCaseTest {

    @Mock SalesDocumentRepository salesDocumentRepository;

    AttachDocumentFileUseCase useCase;

    private SalesDocument document;

    @BeforeEach
    void setUp() {
        useCase = new AttachDocumentFileUseCase(salesDocumentRepository);
        document = issued(null);
        lenient().when(salesDocumentRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private SalesDocument issued(String fileUrl) {
        return SalesDocument.issue(
                UUID.randomUUID(),
                SalesDocumentType.BOLETA,
                "153",
                TaxBreakdown.fromGross(Money.of(BigDecimal.valueOf(79000), "CLP"), BigDecimal.valueOf(19)),
                null,
                "Ana Perez",
                "ana@correo.cl",
                fileUrl,
                UUID.randomUUID(),
                null);
    }

    @Test
    void files_the_picture_on_a_document_that_had_none() {
        when(salesDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        SalesDocument saved = useCase.execute(document.getId(), "boleta-153.png");

        assertEquals("boleta-153.png", saved.getFileUrl());
    }

    /** The folio and the amounts are what the document says; attaching must not touch either. */
    @Test
    void nothing_else_about_the_document_moves() {
        when(salesDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        SalesDocument saved = useCase.execute(document.getId(), "boleta-153.png");

        assertEquals("153", saved.getFolio());
        assertEquals(SalesDocumentStatus.ISSUED, saved.getStatus());
        assertEquals(0, saved.getTotalAmount().amount().compareTo(BigDecimal.valueOf(79000)));
    }

    /**
     * The one thing an append-only record exists to prevent: swapping the picture of a boleta for
     * another one after the fact.
     */
    @Test
    void an_existing_attachment_cannot_be_replaced() {
        SalesDocument withFile = issued("la-original.png");
        when(salesDocumentRepository.findById(withFile.getId())).thenReturn(Optional.of(withFile));

        assertThrows(DomainException.class, () -> useCase.execute(withFile.getId(), "otra.png"));
        verify(salesDocumentRepository, never()).save(any());
    }

    /** A voided document keeps the file it was voided with, because that file is the evidence. */
    @Test
    void a_voided_document_does_not_accept_a_new_file() {
        document.voidDocument("Folio equivocado", UUID.randomUUID());
        when(salesDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        assertThrows(DomainException.class, () -> useCase.execute(document.getId(), "boleta.png"));
        verify(salesDocumentRepository, never()).save(any());
    }

    @Test
    void an_empty_name_is_not_a_file() {
        when(salesDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        assertThrows(DomainException.class, () -> useCase.execute(document.getId(), "   "));
        verify(salesDocumentRepository, never()).save(any());
    }

    @Test
    void a_document_that_does_not_exist_is_refused() {
        UUID missing = UUID.randomUUID();
        when(salesDocumentRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(DomainException.class, () -> useCase.execute(missing, "boleta.png"));
    }
}
