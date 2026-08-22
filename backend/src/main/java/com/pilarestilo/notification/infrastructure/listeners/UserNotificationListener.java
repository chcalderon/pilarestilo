package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.notification.application.UserNotificationDispatcher;
import com.pilarestilo.user.domain.events.UserRegistered;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * In-process transport for user notifications. Dead whenever Kafka is enabled, because
 * {@code KafkaDomainEventPublisher} is {@code @Primary} then — see
 * {@link UserNotificationDispatcher}, which holds the behaviour both transports share.
 */
@Component
public class UserNotificationListener {

    private final UserNotificationDispatcher dispatcher;

    public UserNotificationListener(UserNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventListener
    public void onUserRegistered(UserRegistered event) {
        dispatcher.onUserRegistered(event);
    }
}
