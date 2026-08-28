package com.pilarestilo.payment.infrastructure.listeners;

import com.pilarestilo.payment.application.MarkOrderUnderReviewOnPaymentSubmittedHandler;
import com.pilarestilo.payment.domain.events.PaymentSubmitted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * In-process transport for {@link PaymentSubmitted} → order moves to PAYMENT_UNDER_REVIEW. Dead
 * whenever Kafka is enabled; the behaviour lives in {@link MarkOrderUnderReviewOnPaymentSubmittedHandler}.
 */
@Component
public class MarkOrderUnderReviewOnPaymentSubmittedListener {

    private final MarkOrderUnderReviewOnPaymentSubmittedHandler handler;

    public MarkOrderUnderReviewOnPaymentSubmittedListener(MarkOrderUnderReviewOnPaymentSubmittedHandler handler) {
        this.handler = handler;
    }

    @EventListener
    public void onPaymentSubmitted(PaymentSubmitted event) {
        handler.handle(event.paymentId());
    }
}
