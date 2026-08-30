package com.pilarestilo.order.application.usecases;

import com.pilarestilo.discount.application.DiscountRedemptionService;
import com.pilarestilo.inventory.application.InventoryService;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Everything the status funnel unwinds when an order reaches PAID or CANCELLED.
 *
 * <p>Both the discount ledger and the inventory reservation hang off this one method, because it
 * is the only thing every route to those states passes through. Each half got here after the same
 * defect: a reserved redemption stayed PENDING forever, and a reservation released only from the
 * payment saga left stock held for good whenever an admin cancelled by hand.
 */
@ExtendWith(MockitoExtension.class)
class UpdateOrderStatusUseCaseSideEffectsTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock OrderRepository orderRepository;
    @Mock DomainEventPublisher eventPublisher;
    @Mock DiscountRedemptionService discountRedemptionService;
    @Mock InventoryService inventoryService;
    @InjectMocks UpdateOrderStatusUseCase useCase;

    private Order order;

    @BeforeEach
    void setUp() {
        order = orderInStatus(OrderStatus.CREATED);
    }

    private Order orderInStatus(OrderStatus status) {
        Money price = Money.of(BigDecimal.valueOf(10_000));
        // reconstruct rather than create: the id has to be known so the redemption calls can be
        // asserted against it.
        return Order.reconstruct(
                ORDER_ID, CUSTOMER_ID,
                List.of(new OrderItem(UUID.randomUUID(), PRODUCT_ID, "Vestido", price, 2, "Rojo", "M")),
                price, Money.zero(), price,
                PaymentMethod.TRANSFER,
                "LOCAL", "starken", "Starken", "POR_PAGAR",
                UUID.randomUUID(), "Calle 1", null,
                status, Instant.now(), Instant.now());
    }

    private void stubLocalPath() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void paid_settlesTheRedemption() {
        order = orderInStatus(OrderStatus.PENDING_PAYMENT); // markAsPaid only accepts this
        stubLocalPath();

        useCase.execute(ORDER_ID, OrderStatus.PAID);

        verify(discountRedemptionService).settle(ORDER_ID);
        verify(discountRedemptionService, never()).release(any());
    }

    // -----------------------------------------------------------------------------------------
    // Inventory. These used to live in OrderInventorySaga, reachable only from a payment event.
    // -----------------------------------------------------------------------------------------

    /** The gap this closes: cancelling by hand left the units reserved with nothing to free them. */
    @Test
    void cancelled_givesTheReservationBack() {
        stubLocalPath();

        useCase.execute(ORDER_ID, OrderStatus.CANCELLED);

        verify(inventoryService).release(eq(PRODUCT_ID), eq(2), eq("Rojo"), eq("M"),
                argThat(origin -> ORDER_ID.equals(origin.referenceId())));
    }

    @Test
    void paid_turnsTheReservationIntoASale() {
        order = orderInStatus(OrderStatus.PENDING_PAYMENT);
        stubLocalPath();

        useCase.execute(ORDER_ID, OrderStatus.PAID);

        verify(inventoryService).confirm(eq(PRODUCT_ID), eq(2), eq("Rojo"), eq("M"),
                argThat(origin -> ORDER_ID.equals(origin.referenceId())));
        verify(inventoryService, never()).release(any(), anyInt(), any(), any(), any());
    }

    /**
     * Reaching PAID took the units out of both on-hand and reserved, so a later cancellation puts
     * them back on the shelf rather than releasing a reservation that no longer exists.
     *
     * <p>This used to return nothing at all, on the reasoning that the goods had left the
     * warehouse — but a sale undone before it ships has its garments back on the rack, and the
     * units were simply lost.
     */
    @Test
    void cancelledAfterPaid_putsTheUnitsBackOnTheShelf() {
        order = orderInStatus(OrderStatus.PAID);
        stubLocalPath();

        useCase.execute(ORDER_ID, OrderStatus.CANCELLED);

        verify(inventoryService).returnToStock(eq(PRODUCT_ID), eq(2), eq("Rojo"), eq("M"),
                argThat(origin -> ORDER_ID.equals(origin.referenceId())));
        verify(inventoryService, never()).release(any(), anyInt(), any(), any(), any());
        // The discount is still released here; only PENDING redemptions respond, so a settled
        // one stays settled. That guard lives in the adapter, not in this branch.
        verify(discountRedemptionService).release(ORDER_ID);
    }

    /** And the reverse: while the stock is merely held, a cancellation frees the reservation. */
    @Test
    void cancelledBeforePaid_neverReturnsToStock() {
        stubLocalPath();

        useCase.execute(ORDER_ID, OrderStatus.CANCELLED);

        verify(inventoryService, never()).returnToStock(any(), anyInt(), any(), any(), any());
    }

    /** The auto-cancel job and the admin cancel button both land here. */
    @Test
    void cancelled_releasesTheRedemption() {
        stubLocalPath();

        useCase.execute(ORDER_ID, OrderStatus.CANCELLED);

        verify(discountRedemptionService).release(ORDER_ID);
        verify(discountRedemptionService, never()).settle(any());
    }

    @Test
    void otherTransitions_leaveTheRedemptionAlone() {
        order = orderInStatus(OrderStatus.PAID); // markAsPreparingOrder only accepts this
        stubLocalPath();

        useCase.execute(ORDER_ID, OrderStatus.PREPARING_ORDER);

        verifyNoInteractions(discountRedemptionService);
        verifyNoInteractions(inventoryService);
    }

    /**
     * A no-op transition returns early, before the ledger is touched. Without this, re-sending the
     * same status would settle twice -- harmless today because the adapter keys on status =
     * 'PENDING', but the early return is the first of the three layers guarding that.
     */
    @Test
    void sameStatus_doesNothing() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        useCase.execute(ORDER_ID, order.getStatus());

        verifyNoInteractions(discountRedemptionService);
        verifyNoInteractions(inventoryService);
        verify(orderRepository, never()).save(any());
    }
}
