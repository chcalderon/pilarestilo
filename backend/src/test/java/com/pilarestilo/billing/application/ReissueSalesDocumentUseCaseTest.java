package com.pilarestilo.billing.application;

import com.pilarestilo.billing.application.dto.SalesDocumentDto;
import com.pilarestilo.billing.application.usecases.IssueSalesDocumentUseCase;
import com.pilarestilo.billing.application.usecases.ReissueSalesDocumentUseCase;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Correcting a boleta: the old one is voided and the new one points back at it, in one transaction.
 *
 * <p>Split into two calls there would be a window where the order has no live document, and that is
 * exactly what the dispatch gate reads.
 */
@ExtendWith(MockitoExtension.class)
class ReissueSalesDocumentUseCaseTest {

    @Mock
    SalesDocumentRepository salesDocumentRepository;
    @Mock
    IssueSalesDocumentUseCase issueSalesDocumentUseCase;

    ReissueSalesDocumentUseCase useCase;

    private final UUID actor = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private SalesDocument previous;

    @BeforeEach
    void setUp() {
        useCase = new ReissueSalesDocumentUseCase(salesDocumentRepository, issueSalesDocumentUseCase);
        previous = SalesDocument.issue(orderId, SalesDocumentType.BOLETA, "1041",
                TaxBreakdown.fromGross(Money.of(new BigDecimal("45990")), new BigDecimal("19.00")),
                null, null, null, null, null, null, actor, null);
    }

    @Test
    void voids_the_previous_document_and_chains_the_new_one_to_it() {
        when(salesDocumentRepository.findById(previous.getId())).thenReturn(Optional.of(previous));
        when(issueSalesDocumentUseCase.issue(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(dtoFor("1042"));

        useCase.execute(previous.getId(), "Folio equivocado", SalesDocumentType.BOLETA,
                "1042", "12.345.678-9", null, null, "file.pdf", actor);

        assertEquals(SalesDocumentStatus.VOIDED, previous.getStatus());
        assertEquals("Folio equivocado", previous.getVoidReason());
        verify(salesDocumentRepository).save(previous);

        ArgumentCaptor<UUID> replaces = ArgumentCaptor.forClass(UUID.class);
        verify(issueSalesDocumentUseCase).issue(
                eq(orderId), eq(SalesDocumentType.BOLETA), eq("1042"),
                eq("12.345.678-9"), eq(null), eq(null), eq("file.pdf"), eq(actor), replaces.capture());
        assertEquals(previous.getId(), replaces.getValue());
    }

    /** The razon social and giro travel through the correction exactly like the RUT does. */
    @Test
    void reissuing_a_factura_carries_the_receivers_business_identity() {
        when(salesDocumentRepository.findById(previous.getId())).thenReturn(Optional.of(previous));
        when(issueSalesDocumentUseCase.issue(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(dtoFor("500"));

        useCase.execute(previous.getId(), "Folio equivocado", SalesDocumentType.FACTURA,
                "500", "76.123.456-7", "Comercial Ana Perez Ltda.", "Venta de ropa", null, actor);

        verify(issueSalesDocumentUseCase).issue(
                eq(orderId), eq(SalesDocumentType.FACTURA), eq("500"),
                eq("76.123.456-7"), eq("Comercial Ana Perez Ltda."), eq("Venta de ropa"),
                eq(null), eq(actor), any());
    }

    /** Without a reason the void is unaccountable, so nothing at all happens. */
    @Test
    void refuses_to_reissue_without_a_reason() {
        when(salesDocumentRepository.findById(previous.getId())).thenReturn(Optional.of(previous));

        assertThrows(DomainException.class, () -> useCase.execute(
                previous.getId(), "  ", SalesDocumentType.BOLETA, "1042", null, null, null, null, actor));

        assertEquals(SalesDocumentStatus.ISSUED, previous.getStatus());
        verify(salesDocumentRepository, never()).save(any());
        verify(issueSalesDocumentUseCase, never()).issue(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void refuses_a_document_that_does_not_exist() {
        UUID unknown = UUID.randomUUID();
        when(salesDocumentRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(DomainException.class, () -> useCase.execute(
                unknown, "Folio equivocado", SalesDocumentType.BOLETA, "1042", null, null, null, null, actor));

        verify(issueSalesDocumentUseCase, never()).issue(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private SalesDocumentDto dtoFor(String folio) {
        return new SalesDocumentDto(
                UUID.randomUUID(), orderId, "BOLETA", folio, java.time.Instant.now(),
                new BigDecimal("38647"), new BigDecimal("7343"), new BigDecimal("19.00"),
                new BigDecimal("45990"), "CLP", null, null, null, null, null, false,
                "ISSUED", null, null, previous.getId(), null, actor);
    }
}
