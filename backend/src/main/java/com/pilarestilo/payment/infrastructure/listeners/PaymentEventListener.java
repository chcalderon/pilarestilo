package com.pilarestilo.payment.infrastructure.listeners;

import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;

    public PaymentEventListener(UpdateOrderStatusUseCase updateOrderStatusUseCase) {
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
    }

    @EventListener
    public void onPaymentConfirmed(PaymentConfirmed event) {
        updateOrderStatusUseCase.execute(event.orderId(), OrderStatus.PAID);
    }
}
