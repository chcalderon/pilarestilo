package com.pilarestilo.payment.infrastructure.listeners;

import com.pilarestilo.order.application.sagas.OrderInventorySaga;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

    private final OrderInventorySaga orderInventorySaga;

    public PaymentEventListener(OrderInventorySaga orderInventorySaga) {
        this.orderInventorySaga = orderInventorySaga;
    }

    @EventListener
    public void onPaymentConfirmed(PaymentConfirmed event) {
        orderInventorySaga.onPaymentConfirmed(event);
    }

    @EventListener
    public void onPaymentRejected(PaymentRejected event) {
        orderInventorySaga.onPaymentRejected(event);
    }
}
