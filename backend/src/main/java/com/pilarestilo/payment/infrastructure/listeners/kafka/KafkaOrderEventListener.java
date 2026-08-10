package com.pilarestilo.payment.infrastructure.listeners.kafka;

import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.application.usecases.RegisterPaymentUseCase;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
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

    public KafkaOrderEventListener(RegisterPaymentUseCase registerPaymentUseCase,
                                   OrderRepository orderRepository,
                                   PaymentRepository paymentRepository) {
        this.registerPaymentUseCase = registerPaymentUseCase;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-payment",
            topics = "#{@domainEventTopics.topicFor('OrderCreated')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onOrderCreated(OrderCreated event) {
        if (paymentRepository.findByOrderId(event.orderId()).isPresent()) {
            return;
        }
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new DomainException("Order not found during payment registration: " + event.orderId()));
        registerPaymentUseCase.execute(event.orderId(), order.getPaymentMethod());
    }
}
