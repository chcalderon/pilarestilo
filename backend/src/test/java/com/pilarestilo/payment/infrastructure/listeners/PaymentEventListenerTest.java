package com.pilarestilo.payment.infrastructure.listeners;

import com.pilarestilo.order.application.sagas.OrderInventorySaga;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    @Mock
    OrderInventorySaga orderInventorySaga;

    @InjectMocks
    PaymentEventListener listener;

    @Test
    void delegates_confirmed_event_to_saga() {
        UUID orderId = UUID.randomUUID();
        PaymentConfirmed event = new PaymentConfirmed(UUID.randomUUID(), orderId, Instant.now());

        listener.onPaymentConfirmed(event);

        verify(orderInventorySaga).onPaymentConfirmed(event);
    }

    @Test
    void delegates_rejected_event_to_saga() {
        UUID orderId = UUID.randomUUID();
        PaymentRejected event = new PaymentRejected(UUID.randomUUID(), orderId, UUID.randomUUID(), Instant.now());

        listener.onPaymentRejected(event);

        verify(orderInventorySaga).onPaymentRejected(event);
    }
}
