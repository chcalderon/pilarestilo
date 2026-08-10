package com.pilarestilo.notification.infrastructure.listeners.kafka;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaOrderNotificationListener {

    private final NotificationSender notificationSender;
    private final UserRepository userRepository;

    public KafkaOrderNotificationListener(NotificationSender notificationSender,
                                          UserRepository userRepository) {
        this.notificationSender = notificationSender;
        this.userRepository = userRepository;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('OrderCreated')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onOrderCreated(OrderCreated event) {
        userRepository.findById(event.customerId())
                .ifPresent(user -> notificationSender.sendOrderConfirmation(
                        event.orderId(),
                        NotificationRecipient.of(
                                user.getPhone(),
                                user.getEmail(),
                                user.getNotificationChannelPreference().name()
                        )
                ));
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('OrderStatusChanged')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (event.newStatus() == OrderStatus.PREPARING_ORDER) {
            userRepository.findById(event.customerId())
                    .ifPresentOrElse(
                            user -> notificationSender.sendOrderPreparing(
                                    event.orderId(),
                                    NotificationRecipient.of(
                                            user.getPhone(),
                                            user.getEmail(),
                                            user.getNotificationChannelPreference().name()
                                    )
                            ),
                            () -> notificationSender.sendOrderPreparing(event.orderId(), NotificationRecipient.unknown())
                    );
            return;
        }
        if (event.newStatus() != OrderStatus.SHIPPED) return;

        userRepository.findById(event.customerId())
                .ifPresentOrElse(
                        user -> notificationSender.sendOrderShipped(
                                event.orderId(),
                                NotificationRecipient.of(
                                        user.getPhone(),
                                        user.getEmail(),
                                        user.getNotificationChannelPreference().name()
                                )
                        ),
                        () -> notificationSender.sendOrderShipped(event.orderId(), NotificationRecipient.unknown())
                );
    }
}
