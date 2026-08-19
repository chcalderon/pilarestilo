package com.pilarestilo.returns.application;

import com.pilarestilo.inventory.domain.model.StockMovementOrigin;
import com.pilarestilo.inventory.application.InventoryService;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.returns.application.usecases.ResolveItemDispositionUseCase;
import com.pilarestilo.returns.domain.enums.ItemDisposition;
import com.pilarestilo.returns.domain.enums.ReturnKind;
import com.pilarestilo.returns.domain.model.ReturnRequest;
import com.pilarestilo.returns.domain.ports.ReturnRequestRepository;
import com.pilarestilo.shared.application.Money;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The only place in the module that moves stock, and it moves it late on purpose: every returned
 * garment is cleaned, pressed and repaired first, so arriving is not the same as being sellable.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResolveItemDispositionUseCaseTest {

    private static final UUID RESOLVER = UUID.randomUUID();

    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock
    ReturnRequestRepository returnRequestRepository;
    @Mock
    OrderRepository orderRepository;
    @Mock
    InventoryService inventoryService;

    ResolveItemDispositionUseCase useCase;

    private ReturnRequest request;
    private Order order;

    @BeforeEach
    void setUp() {
        useCase = new ResolveItemDispositionUseCase(
                returnRequestRepository, orderRepository, inventoryService);
        order = orderWithOneItem();
        request = ReturnRequest.open(order.getId(), ReturnKind.RETRACTO, "Me arrepenti", UUID.randomUUID());
        request.approve();
        request.receive();

        when(returnRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(returnRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    }

    @Test
    void a_reconditioned_garment_goes_back_on_the_shelf() {
        useCase.execute(request.getId(), ItemDisposition.RESTOCKED, null, RESOLVER);

        // returnToStock, never release: the units were confirmed out of both columns at payment,
        // so releasing would decrement a reservation that no longer exists.
        // And the ledger line says which return moved them, and who judged the garment sellable.
        verify(inventoryService).returnToStock(eq(PRODUCT_ID), eq(2), eq("Rojo"), eq("M"),
                argThat(origin -> StockMovementOrigin.RETURN_REQUEST.equals(origin.referenceType())
                        && request.getId().equals(origin.referenceId())
                        && RESOLVER.equals(origin.recordedBy())));
        verify(inventoryService, never()).release(any(), anyInt(), any(), any(), any());
        assertEquals(ItemDisposition.RESTOCKED, request.getItemDisposition());
    }

    @Test
    void a_discarded_garment_moves_no_stock_and_says_why() {
        useCase.execute(request.getId(), ItemDisposition.DISCARDED, "Mancha irrecuperable", RESOLVER);

        verifyNoInteractions(inventoryService);
        assertEquals(ItemDisposition.DISCARDED, request.getItemDisposition());
        assertEquals("Mancha irrecuperable", request.getDispositionNote());
    }

    @Test
    void discarding_without_a_reason_moves_nothing_at_all() {
        assertThrows(DomainException.class,
                () -> useCase.execute(request.getId(), ItemDisposition.DISCARDED, "  ", RESOLVER));

        verifyNoInteractions(inventoryService);
        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    void a_garment_that_never_arrived_cannot_be_disposed_of() {
        ReturnRequest notReceived = ReturnRequest.open(
                order.getId(), ReturnKind.DEVOLUCION, "No le quedo", null);
        notReceived.approve();
        when(returnRequestRepository.findById(notReceived.getId())).thenReturn(Optional.of(notReceived));

        assertThrows(DomainException.class,
                () -> useCase.execute(notReceived.getId(), ItemDisposition.RESTOCKED, null, RESOLVER));

        verifyNoInteractions(inventoryService);
    }

    @Test
    void refuses_a_return_that_does_not_exist() {
        UUID unknown = UUID.randomUUID();
        when(returnRequestRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(DomainException.class,
                () -> useCase.execute(unknown, ItemDisposition.RESTOCKED, null, RESOLVER));
    }

    private Order orderWithOneItem() {
        return Order.create(
                UUID.randomUUID(),
                List.of(new OrderItem(UUID.randomUUID(), PRODUCT_ID, "Vestido lino",
                        Money.of(new BigDecimal("45990")), 2, "Rojo", "M")),
                Money.zero(),
                PaymentMethod.TRANSFER,
                "LOCAL", "starken", "Starken", "POR_PAGAR",
                UUID.randomUUID(), "Santa Angela 92", null, null, new BigDecimal("19.00"));
    }
}
