package com.pilarestilo.notification.infrastructure.listeners.kafka;

import com.pilarestilo.notification.application.NotificationComposer;
import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.application.BankTransferDeadline;
import com.pilarestilo.payment.domain.events.PaymentRegistered;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Kafka twin of {@link com.pilarestilo.notification.infrastructure.listeners.PaymentRegisteredNotificationListener}.
 *
 * <p>Not optional. KafkaDomainEventPublisher is {@code @Primary}, so with
 * {@code APP_DOMAIN_EVENTS_KAFKA_ENABLED=true} — which production runs — the in-process
 * {@code @EventListener} never fires. Without this class the transfer instructions would simply
 * never be sent there, silently.
 */
@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaPaymentRegisteredNotificationListener {

    private final NotificationSender notificationSender;
    private final NotificationComposer notificationComposer;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final SystemSettingsRepository systemSettingsRepository;

    public KafkaPaymentRegisteredNotificationListener(NotificationSender notificationSender,
                                                      NotificationComposer notificationComposer,
                                                      PaymentRepository paymentRepository,
                                                      OrderRepository orderRepository,
                                                      UserRepository userRepository,
                                                      SystemSettingsRepository systemSettingsRepository) {
        this.notificationSender = notificationSender;
        this.notificationComposer = notificationComposer;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.systemSettingsRepository = systemSettingsRepository;
    }

    /**
     * Read-only transaction because a Kafka listener runs outside any request and open-in-view is
     * disabled, so the repository reads below would otherwise have no session.
     */
    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('PaymentRegistered')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    @Transactional(readOnly = true)
    public void onPaymentRegistered(PaymentRegistered event) {
        paymentRepository.findById(event.paymentId()).ifPresent(payment -> {
            if (payment.getMethod() != PaymentMethod.TRANSFER) {
                return;
            }
            orderRepository.findById(event.orderId()).ifPresent(order -> {
                NotificationRecipient recipient = userRepository.findById(order.getCustomerId())
                        .map(user -> NotificationRecipient.of(user.getPhone(), user.getEmail(),
                                user.getNotificationChannelPreference().name()))
                        .orElse(NotificationRecipient.unknown());

                Instant deadline = BankTransferDeadline
                        .forPayment(payment, systemSettingsRepository.get())
                        .orElse(null);

                notificationSender.send(
                        notificationComposer.transferInstructions(order, payment, deadline),
                        recipient);
            });
        });
    }
}
