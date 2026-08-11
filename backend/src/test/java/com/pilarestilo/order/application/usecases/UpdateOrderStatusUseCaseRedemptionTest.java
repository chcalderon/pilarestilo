package com.pilarestilo.order.application.usecases;

import com.pilarestilo.discount.application.DiscountRedemptionService;
import com.pilarestilo.order.application.remote.OrderRemoteCommandClient;
import com.pilarestilo.order.application.remote.OrderRemoteQueryClient;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The discount half of the status funnel. A reserved redemption is worthless without these two
 * calls: before this wiring existed, a code applied at checkout stayed PENDING forever.
 */
@ExtendWith(MockitoExtension.class)
class UpdateOrderStatusUseCaseRedemptionTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    @Mock OrderRepository orderRepository;
    @Mock DomainEventPublisher eventPublisher;
    @Mock OrderRemoteCommandClient orderRemoteCommandClient;
    @Mock OrderRemoteQueryClient orderRemoteQueryClient;
    @Mock DiscountRedemptionService discountRedemptionService;
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
                List.of(new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "Vestido", price, 1)),
                price, Money.zero(), price,
                PaymentMethod.TRANSFER,
                "LOCAL", "starken", "Starken", "POR_PAGAR",
                UUID.randomUUID(), "Calle 1", null,
                status, Instant.now(), Instant.now());
    }

    private void stubLocalPath() {
        when(orderRemoteCommandClient.isWriteEnabled()).thenReturn(false);
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
    }

    /**
     * A no-op transition returns early, before the ledger is touched. Without this, re-sending the
     * same status would settle twice -- harmless today because the adapter keys on status =
     * 'PENDING', but the early return is the first of the three layers guarding that.
     */
    @Test
    void sameStatus_doesNothing() {
        when(orderRemoteCommandClient.isWriteEnabled()).thenReturn(false);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        useCase.execute(ORDER_ID, order.getStatus());

        verifyNoInteractions(discountRedemptionService);
        verify(orderRepository, never()).save(any());
    }
}
