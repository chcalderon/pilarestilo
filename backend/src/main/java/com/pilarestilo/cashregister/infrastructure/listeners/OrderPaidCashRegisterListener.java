package com.pilarestilo.cashregister.infrastructure.listeners;

import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import com.pilarestilo.cashregister.domain.model.CashRegister;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPaidCashRegisterListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidCashRegisterListener.class);

    private final CashRegisterRepository cashRegisterRepository;
    private final OrderRepository orderRepository;

    public OrderPaidCashRegisterListener(CashRegisterRepository cashRegisterRepository,
                                          OrderRepository orderRepository) {
        this.cashRegisterRepository = cashRegisterRepository;
        this.orderRepository = orderRepository;
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (event.newStatus() != OrderStatus.PAID) return;

        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) return;

        cashRegisterRepository.findAnyOpenRegister().ifPresentOrElse(cr -> {
            cr.addMovement(
                    CashMovementType.SALE,
                    order.getTotalAmount().amount(),
                    "Venta #" + order.getId().toString().substring(0, 8),
                    order.getId(),
                    cr.getSellerId());
            cashRegisterRepository.save(cr);
        }, () -> log.warn("Order {} paid but no open cash register found — SALE not recorded", event.orderId()));
    }
}
