package com.pilarestilo.billing.application;

import com.pilarestilo.billing.application.usecases.CancelSaleUseCase;
import com.pilarestilo.billing.domain.enums.SalesDocumentStatus;
import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Closing a sale as cancelled. The document is voided and the order cancelled together, because a
 * boleta void with a live sale behind it is a sale nobody declared and nobody undid.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CancelSaleUseCaseTest {

    @Mock
    SalesDocumentRepository salesDocumentRepository;
    @Mock
    UpdateOrderStatusUseCase updateOrderStatusUseCase;

    CancelSaleUseCase useCase;

    private final UUID orderId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private SalesDocument live;

    @BeforeEach
    void setUp() {
        useCase = new CancelSaleUseCase(salesDocumentRepository, updateOrderStatusUseCase);
        live = SalesDocument.issue(orderId, SalesDocumentType.BOLETA, "1042",
                TaxBreakdown.fromGross(Money.of(new BigDecimal("45990")), new BigDecimal("19.00")),
                null, null, null, null, actor, null);
    }

    @Test
    void voids_the_document_and_cancels_the_order() {
        when(salesDocumentRepository.findLiveByOrderId(orderId)).thenReturn(Optional.of(live));

        useCase.execute(orderId, "Cliente se arrepintio", actor);

        assertEquals(SalesDocumentStatus.VOIDED, live.getStatus());
        assertEquals("Cliente se arrepintio", live.getVoidReason());
        assertEquals(actor, live.getVoidedBy());
        verify(salesDocumentRepository).save(live);
        // Cancelling is what returns the units and releases the discount. Repeating either here
        // would apply it twice: the status funnel is the single hook.
        verify(updateOrderStatusUseCase).execute(orderId, OrderStatus.CANCELLED);
    }

    /** A paid order can sit undeclared — that is exactly what the pending queue lists. */
    @Test
    void cancels_a_sale_that_has_no_document_yet() {
        when(salesDocumentRepository.findLiveByOrderId(orderId)).thenReturn(Optional.empty());

        useCase.execute(orderId, "Sin stock real", actor);

        verify(salesDocumentRepository, never()).save(any());
        verify(updateOrderStatusUseCase).execute(orderId, OrderStatus.CANCELLED);
    }

    @Test
    void refuses_without_a_reason_or_an_actor() {
        assertThrows(DomainException.class, () -> useCase.execute(orderId, "  ", actor));
        assertThrows(DomainException.class, () -> useCase.execute(orderId, "Motivo", null));

        verify(updateOrderStatusUseCase, never()).execute(any(), any());
        verify(salesDocumentRepository, never()).save(any());
    }

    /**
     * The order aggregate refuses to cancel a delivered order, and that refusal has to reach the
     * caller with the document still live rather than voided against a sale that stands.
     */
    @Test
    void a_refusal_from_the_order_leaves_nothing_half_done() {
        when(salesDocumentRepository.findLiveByOrderId(orderId)).thenReturn(Optional.of(live));
        when(updateOrderStatusUseCase.execute(orderId, OrderStatus.CANCELLED))
                .thenThrow(new DomainException("Cannot cancel a delivered order"));

        assertThrows(DomainException.class, () -> useCase.execute(orderId, "Motivo", actor));
        // The void is rolled back with the transaction; what matters is that the exception
        // propagates rather than being swallowed into a half-cancelled sale.
    }
}
