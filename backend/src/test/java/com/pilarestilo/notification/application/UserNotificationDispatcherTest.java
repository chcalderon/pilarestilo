package com.pilarestilo.notification.application;

import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.events.UserRegistered;
import com.pilarestilo.user.domain.model.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Same shape as {@link OrderNotificationDispatcherTest}: both listener transports are thin
 * adapters over this class, so covering it covers the in-process and Kafka twins at once.
 */
@ExtendWith(MockitoExtension.class)
class UserNotificationDispatcherTest {

    @Mock NotificationSender notificationSender;
    @Mock InAppNotificationPort inAppNotificationPort;
    @Mock UserRepository userRepository;
    final NotificationComposer composer = new NotificationComposer();

    UserNotificationDispatcher dispatcher;

    final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dispatcher = new UserNotificationDispatcher(
                notificationSender, composer, inAppNotificationPort, userRepository);
    }

    private User registeredUser() {
        User user = User.create("camila@example.com", "Camila Torres", UserRole.CUSTOMER, "hash");
        user.setId(userId);
        return user;
    }

    @Test
    void sendsTheWelcomeMessage() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(registeredUser()));

        dispatcher.onUserRegistered(new UserRegistered(userId, Instant.now()));

        verify(notificationSender).send(
                argThat(m -> NotificationMessage.WELCOME.equals(m.templateKey())
                        && m.bodyText().contains("Camila Torres")),
                any());
    }

    @Test
    void writesTheInAppWelcomeNotification() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(registeredUser()));

        dispatcher.onUserRegistered(new UserRegistered(userId, Instant.now()));

        verify(inAppNotificationPort).notifyWelcome(userId, null);
    }

    @Test
    void writesTheCouponCodeIntoTheInAppNotificationWhenOneWasIssued() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(registeredUser()));
        var coupon = new UserRegistered.WelcomeDiscount(
                "BIENVENIDA-ABC123", "PERCENTAGE", BigDecimal.TEN, BigDecimal.ZERO,
                LocalDate.now().plusDays(30));

        dispatcher.onUserRegistered(new UserRegistered(userId, Instant.now(), coupon));

        verify(inAppNotificationPort).notifyWelcome(userId, "BIENVENIDA-ABC123");
        verify(notificationSender).send(
                argThat(m -> NotificationMessage.WELCOME.equals(m.templateKey())
                        && m.bodyText().contains("BIENVENIDA-ABC123")),
                any());
    }

    /** No row means nothing to greet — no address, no name, nothing worth sending. */
    @Test
    void doesNothingWhenTheUserRowIsMissing() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        dispatcher.onUserRegistered(new UserRegistered(userId, Instant.now()));

        verify(notificationSender, never()).send(any(), any());
        verify(inAppNotificationPort, never()).notifyWelcome(any(), any());
    }
}
