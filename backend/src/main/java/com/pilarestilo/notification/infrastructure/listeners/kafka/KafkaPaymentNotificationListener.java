package com.pilarestilo.notification.infrastructure.listeners.kafka;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaPaymentNotificationListener {

    private final NotificationSender notificationSender;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public KafkaPaymentNotificationListener(NotificationSender notificationSender,
                                            OrderRepository orderRepository,
                                            UserRepository userRepository) {
        this.notificationSender = notificationSender;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('PaymentConfirmed')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    /**
     * A Kafka listener runs outside any request, and open-in-view is disabled, so the two
     * repository reads below have no ambient session. Read-only transaction keeps them on one.
     */
    @Transactional(readOnly = true)
    public void onPaymentConfirmed(PaymentConfirmed event) {
        NotificationRecipient recipient = orderRepository.findById(event.orderId())
                .flatMap(order -> userRepository.findById(order.getCustomerId()))
                .map(user -> NotificationRecipient.of(
                        user.getPhone(),
                        user.getEmail(),
                        user.getNotificationChannelPreference().name()
                ))
                .orElse(NotificationRecipient.unknown());
        notificationSender.sendPaymentReceived(event.paymentId(), recipient);
    }
}
