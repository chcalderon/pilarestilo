package com.pilarestilo.cashregister.application.usecases;

import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import com.pilarestilo.cashregister.domain.model.CashRegister;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Records a SALE cash movement when a POS order is paid with cash.
 *
 * Only fires for POS channel orders; ECOMMERCE and other channels are intentionally ignored.
 * Non-cash POS payment methods (e.g. transfer) are also silently ignored — only physical
 * cash at the counter needs to be recorded in the cash register.
 */
@Service
public class RegisterPosSaleUseCase {

    private final CashRegisterRepository cashRegisterRepository;
    private final OrderRepository orderRepository;

    public RegisterPosSaleUseCase(CashRegisterRepository cashRegisterRepository,
                                   OrderRepository orderRepository) {
        this.cashRegisterRepository = cashRegisterRepository;
        this.orderRepository = orderRepository;
    }

    public void execute(UUID orderId, SalesChannel salesChannel, PaymentMethod paymentMethod) {
        if (salesChannel != SalesChannel.POS) {
            throw new IllegalArgumentException("RegisterPosSaleUseCase requires POS channel");
        }

        // TODO Fase 3: rename to PaymentMethod.CASH
        if (paymentMethod != PaymentMethod.CASH_ON_DELIVERY) {
            // Non-cash POS sales (e.g. card, transfer) do not affect the cash register
            return;
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        CashRegister cashRegister = cashRegisterRepository.findAnyOpenRegister()
                .orElseThrow(() -> new IllegalStateException("No open cash register found"));

        cashRegister.addMovement(
                CashMovementType.SALE,
                order.getTotalAmount().amount(),
                "Venta POS #" + orderId.toString().substring(0, 8),
                orderId,
                cashRegister.getSellerId()
        );

        cashRegisterRepository.save(cashRegister);
    }
}
