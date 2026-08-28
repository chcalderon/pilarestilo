package com.pilarestilo.notificationservice;

import com.pilarestilo.notificationservice.domain.enums.NotificationType;
import com.pilarestilo.notificationservice.domain.model.InAppNotification;
import com.pilarestilo.notificationservice.domain.ports.InAppNotificationRepository;
import com.pilarestilo.notificationservice.support.AbstractSharedStackIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The owned database, asserted rather than assumed: a saved notification lands in
 * {@code pilarestilo_notifications}, its Flyway migration ran, and the read paths come back from
 * the same place. Booting the full context here is also the real "context loads" check.
 */
@SpringBootTest
class NotificationStoreIT extends AbstractSharedStackIT {

    @Autowired
    InAppNotificationRepository repository;

    @Test
    void a_saved_notification_is_counted_as_unread() {
        UUID userId = UUID.randomUUID();

        repository.save(InAppNotification.create(userId, NotificationType.ORDER_CONFIRMED,
                "Pedido confirmado", "Cuerpo", Map.of("orderId", UUID.randomUUID().toString())));

        assertThat(repository.countUnreadByUserId(userId)).isEqualTo(1);
    }

    @Test
    void marking_read_flips_is_read_and_clears_the_unread_count() {
        UUID userId = UUID.randomUUID();
        InAppNotification saved = repository.save(InAppNotification.create(userId,
                NotificationType.PAYMENT_RECEIVED, "Pago recibido", "Cuerpo", Map.of()));

        repository.markAsRead(saved.getId(), userId);

        assertThat(repository.findByIdAndUserId(saved.getId(), userId))
                .get()
                .matches(InAppNotification::isRead);
        assertThat(repository.countUnreadByUserId(userId)).isZero();
    }

    @Test
    void reads_are_scoped_to_the_owning_user() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        InAppNotification saved = repository.save(InAppNotification.create(owner,
                NotificationType.WELCOME, "Bienvenida", "Cuerpo", Map.of()));

        assertThat(repository.findByIdAndUserId(saved.getId(), other)).isEmpty();
        assertThat(repository.findByUserId(other, PageRequest.of(0, 10)).getTotalElements()).isZero();
    }
}
