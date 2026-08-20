package com.pilarestilo.notifications;

import com.pilarestilo.notification.domain.enums.NotificationType;
import com.pilarestilo.notification.domain.model.InAppNotification;
import com.pilarestilo.notification.domain.ports.InAppNotificationRepository;
import com.pilarestilo.support.NotificationsTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The point of the whole exercise, asserted rather than assumed.
 *
 * <p>Two DataSources that both start are not evidence of anything: the wiring can look right and
 * write to the wrong database, or to none. This repository has been here before -- a Redis cache
 * that stored nothing for months because the failure was handled as a miss -- and this module hides
 * that failure the same way, since InAppNotificationSender logs and swallows.
 *
 * <p>So both sides are checked. The row has to be in the notifications database, and the old table
 * -- which still exists, V31 created it and it is still there until a later migration drops it --
 * has to be empty. Only the second half would catch a factory still pointed at the old database.
 */
@SpringBootTest
@Testcontainers
class NotificationsUseTheirOwnDatabaseIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        NotificationsTestDatabase.register(registry, postgres);
    }

    @Autowired
    InAppNotificationRepository repository;

    @Value("${app.notification.datasource.url}")
    String notificationsUrl;

    @Test
    void a_saved_notification_lands_in_the_notifications_database_and_not_the_old_one() {
        UUID userId = UUID.randomUUID();

        repository.save(InAppNotification.create(
                userId, NotificationType.ORDER_CONFIRMED, "Pedido confirmado", "Cuerpo",
                Map.of("orderId", UUID.randomUUID().toString())));

        assertThat(countFor(notificationsUrl, userId))
                .as("the notification did not reach the notifications database")
                .isEqualTo(1);

        assertThat(countFor(postgres.getJdbcUrl(), userId))
                .as("the notification was written to the old database -- the boundary is not real")
                .isZero();
    }

    @Test
    void reads_come_back_from_the_notifications_database() {
        UUID userId = UUID.randomUUID();
        repository.save(InAppNotification.create(
                userId, NotificationType.PAYMENT_RECEIVED, "Pago recibido", "Cuerpo", Map.of()));

        assertThat(repository.countUnreadByUserId(userId)).isEqualTo(1);
    }

    private long countFor(String jdbcUrl, UUID userId) {
        String sql = "SELECT count(*) FROM notifications WHERE user_id = ?";
        try (Connection connection = DriverManager.getConnection(
                        jdbcUrl, postgres.getUsername(), postgres.getPassword());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not count notifications in " + jdbcUrl, ex);
        }
    }
}
