package com.pilarestilo.notification.infrastructure.listeners.kafka;

import com.pilarestilo.notification.application.NotificationComposer;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
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
    private final NotificationComposer notificationComposer;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public KafkaPaymentNotificationListener(NotificationSender notificationSender,
                                            NotificationComposer notificationComposer,
                                            PaymentRepository paymentRepository,
                                            OrderRepository orderRepository,
                                            UserRepository userRepository) {
        this.notificationSender = notificationSender;
        this.notificationComposer = notificationComposer;
        this.paymentRepository = paymentRepository;
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

    /**
     * PaymentRejected had no handler here at all, so with Kafka on -- which production runs -- a
     * customer whose transfer was auto-cancelled was never told. This gap sat in front of the
     * empty-default bug on the port, so fixing only the port would not have surfaced it.
     */
    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('PaymentRejected')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    @Transactional(readOnly = true)
    public void onPaymentRejected(PaymentRejected event) {
        paymentRepository.findById(event.paymentId()).ifPresent(payment ->
            orderRepository.findById(event.orderId()).ifPresent(order -> {
                NotificationRecipient recipient = userRepository.findById(order.getCustomerId())
                        .map(user -> NotificationRecipient.of(user.getPhone(), user.getEmail(),
                                user.getNotificationChannelPreference().name()))
                        .orElse(NotificationRecipient.unknown());
                notificationSender.send(
                        notificationComposer.orderCancelled(order, payment.getRejectionReason()),
                        recipient);
            }));
    }
}
