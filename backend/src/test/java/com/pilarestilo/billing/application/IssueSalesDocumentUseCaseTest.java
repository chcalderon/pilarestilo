package com.pilarestilo.billing.application;

import com.pilarestilo.billing.application.dto.SalesDocumentDto;
import com.pilarestilo.billing.application.usecases.IssueSalesDocumentUseCase;
import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.events.SalesDocumentIssued;
import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.application.TaxBreakdown;
import com.pilarestilo.shared.application.AfterCommitPublisher;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IssueSalesDocumentUseCaseTest {

    @Mock
    SalesDocumentRepository salesDocumentRepository;
    @Mock
    OrderRepository orderRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    AfterCommitPublisher eventPublisher;

    IssueSalesDocumentUseCase useCase;

    private final UUID actor = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private Order order;

    @BeforeEach
    void setUp() {
        useCase = new IssueSalesDocumentUseCase(
                salesDocumentRepository, orderRepository, userRepository, eventPublisher);
        order = paidOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(salesDocumentRepository.findLiveByOrderId(order.getId())).thenReturn(Optional.empty());
        when(salesDocumentRepository.existsByTypeAndFolio(any(), any())).thenReturn(false);
        when(salesDocumentRepository.save(any(SalesDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());
    }

    @Test
    void registers_the_document_with_the_amounts_of_the_order() {
        SalesDocumentDto dto = useCase.execute(
                order.getId(), SalesDocumentType.BOLETA, "1042", null, null, null, null, actor);

        assertEquals("1042", dto.folio());
        assertEquals(0, dto.totalAmount().compareTo(new BigDecimal("45990")));
        assertEquals(0, dto.netAmount().compareTo(new BigDecimal("38647")));
        assertEquals(0, dto.taxAmount().compareTo(new BigDecimal("7343")));
        // The document must reconcile: this is what the DB CHECK asserts too.
        assertEquals(0, dto.totalAmount().compareTo(dto.netAmount().add(dto.taxAmount())));
        verify(eventPublisher).publish(any(SalesDocumentIssued.class));
    }

    @Test
    void snapshots_the_buyer_so_the_document_survives_their_deletion() {
        User buyer = User.reconstruct(customerId, "ana@correo.cl", "Ana Perez",
                UserRole.CUSTOMER, true, "hash", Instant.now());
        when(userRepository.findById(customerId)).thenReturn(Optional.of(buyer));

        SalesDocumentDto dto = useCase.execute(
                order.getId(), SalesDocumentType.BOLETA, "1042", null, null, null, null, actor);

        assertEquals("Ana Perez", dto.receiverName());
        assertEquals("ana@correo.cl", dto.receiverEmail());
    }

    @Test
    void the_receiver_rut_stays_absent_when_it_is_not_given() {
        SalesDocumentDto dto = useCase.execute(
                order.getId(), SalesDocumentType.BOLETA, "1042", null, null, null, null, actor);

        assertNull(dto.receiverRut());
    }

    @Test
    void registers_a_factura_with_the_receivers_business_identity() {
        SalesDocumentDto dto = useCase.execute(
                order.getId(), SalesDocumentType.FACTURA, "500",
                "76.123.456-7", "Comercial Ana Perez Ltda.", "Venta de ropa", null, actor);

        assertEquals("76.123.456-7", dto.receiverRut());
        assertEquals("Comercial Ana Perez Ltda.", dto.receiverBusinessName());
        assertEquals("Venta de ropa", dto.receiverBusinessActivity());
    }

    @Test
    void a_factura_without_a_razon_social_is_refused() {
        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(
                order.getId(), SalesDocumentType.FACTURA, "500",
                "76.123.456-7", null, "Venta de ropa", null, actor));

        assertTrue(ex.getMessage().contains("razon social"));
        verify(salesDocumentRepository, never()).save(any());
    }

    /** A document declares money already collected. Anything earlier has no sale to declare. */
    @Test
    void refuses_an_order_that_is_not_paid_yet() {
        Order pending = pendingOrder();
        when(orderRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(
                pending.getId(), SalesDocumentType.BOLETA, "1042", null, null, null, null, actor));

        assertTrue(ex.getMessage().contains("paid sale"));
        verify(salesDocumentRepository, never()).save(any());
    }

    @Test
    void refuses_a_second_live_document_for_the_same_order() {
        when(salesDocumentRepository.findLiveByOrderId(order.getId()))
                .thenReturn(Optional.of(existingDocument()));

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(
                order.getId(), SalesDocumentType.BOLETA, "1043", null, null, null, null, actor));

        assertTrue(ex.getMessage().contains("void it before issuing another"));
        verify(salesDocumentRepository, never()).save(any());
    }

    @Test
    void refuses_a_folio_that_is_already_registered() {
        when(salesDocumentRepository.existsByTypeAndFolio(SalesDocumentType.BOLETA, "1042"))
                .thenReturn(true);

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(
                order.getId(), SalesDocumentType.BOLETA, "1042", null, null, null, null, actor));

        assertTrue(ex.getMessage().contains("already registered"));
        verify(salesDocumentRepository, never()).save(any());
    }

    @Test
    void refuses_an_order_that_does_not_exist() {
        UUID unknown = UUID.randomUUID();
        when(orderRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(DomainException.class, () -> useCase.execute(
                unknown, SalesDocumentType.BOLETA, "1042", null, null, null, null, actor));
    }

    private SalesDocument existingDocument() {
        return SalesDocument.issue(order.getId(), SalesDocumentType.BOLETA, "1041",
                TaxBreakdown.fromGross(Money.of(new BigDecimal("45990")), new BigDecimal("19.00")),
                null, null, null, null, null, null, actor, null);
    }

    private Order paidOrder() {
        Order created = newOrder();
        created.markAsPaid();
        return created;
    }

    private Order pendingOrder() {
        return newOrder();
    }

    private Order newOrder() {
        Order created = Order.create(
                customerId,
                List.of(new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "Vestido lino",
                        Money.of(new BigDecimal("45990")), 1, null, null)),
                Money.zero(),
                PaymentMethod.TRANSFER,
                "LOCAL", "starken", "Starken", "POR_PAGAR",
                UUID.randomUUID(), "Santa Angela 92", null, null, new BigDecimal("19.00"));
        created.markAsPendingPayment();
        assertEquals(OrderStatus.PENDING_PAYMENT, created.getStatus());
        return created;
    }
}
