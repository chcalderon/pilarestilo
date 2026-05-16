package com.pilarestilo.cashregister.application;

import com.pilarestilo.cashregister.application.usecases.RegisterPosSaleUseCase;
import com.pilarestilo.cashregister.domain.enums.CashMovementCategory;
import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import com.pilarestilo.cashregister.domain.enums.CashRegisterStatus;
import com.pilarestilo.cashregister.domain.model.CashRegister;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterPosSaleUseCaseTest {

    @Mock CashRegisterRepository cashRegisterRepository;
    @Mock OrderRepository orderRepository;
    @InjectMocks RegisterPosSaleUseCase useCase;

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void posOrderPaidWithCash_createsCashSaleMovement() {
        UUID orderId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();

        Order order = buildOrder(orderId, SalesChannel.POS, PaymentMethod.CASH,
                BigDecimal.valueOf(25_000));
        CashRegister register = buildOpenRegister(sellerId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(cashRegisterRepository.findAnyOpenRegister()).thenReturn(Optional.of(register));
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(orderId, SalesChannel.POS, PaymentMethod.CASH);

        ArgumentCaptor<CashRegister> saved = ArgumentCaptor.forClass(CashRegister.class);
        verify(cashRegisterRepository).save(saved.capture());

        CashRegister result = saved.getValue();
        assertThat(result.getMovements()).hasSize(1);
        assertThat(result.getMovements().get(0).getType()).isEqualTo(CashMovementType.IN);
        assertThat(result.getMovements().get(0).getCategory()).isEqualTo(CashMovementCategory.CASH_SALE);
        assertThat(result.getMovements().get(0).getAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(25_000));
        assertThat(result.getMovements().get(0).getDescription())
                .startsWith("Venta POS #")
                .contains(orderId.toString().substring(0, 8));
        assertThat(result.getMovements().get(0).getOrderId()).isEqualTo(orderId);
        assertThat(result.getMovements().get(0).getRecordedBy()).isEqualTo(sellerId);
    }

    // ── non-cash POS — silent return ─────────────────────────────────────────

    @Test
    void posOrderPaidWithNonCash_doesNotCreateMovement() {
        UUID orderId = UUID.randomUUID();

        useCase.execute(orderId, SalesChannel.POS, PaymentMethod.TRANSFER);

        verify(cashRegisterRepository, never()).findAnyOpenRegister();
        verify(cashRegisterRepository, never()).save(any());
        verify(orderRepository, never()).findById(any());
    }

    // ── wrong channel ─────────────────────────────────────────────────────────

    @Test
    void ecommerceChannel_throws() {
        UUID orderId = UUID.randomUUID();

        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> useCase.execute(
                        orderId, SalesChannel.ECOMMERCE, PaymentMethod.CASH))
                .withMessageContaining("RegisterPosSaleUseCase requires POS channel");

        verify(cashRegisterRepository, never()).save(any());
        verify(orderRepository, never()).findById(any());
    }

    // ── no open cash register ─────────────────────────────────────────────────

    @Test
    void posOrderPaidWithCash_noOpenCashRegister_throws() {
        UUID orderId = UUID.randomUUID();

        Order order = buildOrder(orderId, SalesChannel.POS, PaymentMethod.CASH,
                BigDecimal.valueOf(10_000));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(cashRegisterRepository.findAnyOpenRegister()).thenReturn(Optional.empty());

        assertThatIllegalStateException()
                .isThrownBy(() -> useCase.execute(
                        orderId, SalesChannel.POS, PaymentMethod.CASH))
                .withMessage("No open cash register found");

        verify(cashRegisterRepository, never()).save(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Order buildOrder(UUID orderId, SalesChannel channel, PaymentMethod method,
                              BigDecimal total) {
        return Order.reconstruct(
                orderId, UUID.randomUUID(),
                List.of(new OrderItem(UUID.randomUUID(), UUID.randomUUID(),
                        "Test", Money.of(total), 1)),
                Money.of(total), Money.zero(), Money.of(total),
                method,
                "RM", "chilexpress", "Chilexpress", "PREPAID",
                UUID.randomUUID(), "Ref",
                null, channel,
                OrderStatus.PAID,
                Instant.now(), Instant.now()
        );
    }

    private CashRegister buildOpenRegister(UUID sellerId) {
        return CashRegister.reconstruct(
                UUID.randomUUID(), sellerId,
                java.time.LocalDateTime.now(), null,
                BigDecimal.valueOf(50_000),
                null, null, null,
                CashRegisterStatus.OPEN,
                null,
                new java.util.ArrayList<>()
        );
    }
}
