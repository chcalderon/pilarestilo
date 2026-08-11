package com.pilarestilo.payment.infrastructure.listeners.kafka;

import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.application.usecases.RegisterPaymentUseCase;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import java.time.Instant;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.payment.domain.events.PaymentRegistered;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaOrderEventListener {

    private final RegisterPaymentUseCase registerPaymentUseCase;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final DomainEventPublisher eventPublisher;

    public KafkaOrderEventListener(RegisterPaymentUseCase registerPaymentUseCase,
                                   OrderRepository orderRepository,
                                   PaymentRepository paymentRepository,
                              DomainEventPublisher eventPublisher) {
        this.registerPaymentUseCase = registerPaymentUseCase;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-payment",
            topics = "#{@domainEventTopics.topicFor('OrderCreated')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onOrderCreated(OrderCreated event) {
        var existing = paymentRepository.findByOrderId(event.orderId());
        if (existing.isPresent()) {
            // order-service writes the payment row itself when APP_ORDER_REMOTE_WRITE_ENABLED is
            // on, and it has no Kafka publisher, so PaymentRegistered was never announced on that
            // path -- transfer instructions silently never reached the customer. Announcing the
            // existing payment closes the gap without teaching order-service to publish events.
            eventPublisher.publish(new PaymentRegistered(
                    existing.get().getId(), event.orderId(), Instant.now()));
            return;
        }
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new DomainException("Order not found during payment registration: " + event.orderId()));
        registerPaymentUseCase.execute(event.orderId(), order.getPaymentMethod());
    }
}
