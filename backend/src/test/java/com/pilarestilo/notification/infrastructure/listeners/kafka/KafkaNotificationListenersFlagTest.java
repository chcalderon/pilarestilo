package com.pilarestilo.notification.infrastructure.listeners.kafka;

import com.pilarestilo.notification.application.OrderNotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@code app.notification.kafka-listeners.enabled} is a second gate on the notification Kafka
 * consumers — what the notification-service cutover flips to silence this monolith's consumers
 * without touching any other Kafka listener, and what makes the cutover reversible by a flag flip.
 */
class KafkaNotificationListenersFlagTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(OrderNotificationDispatcher.class, () -> mock(OrderNotificationDispatcher.class))
            .withUserConfiguration(KafkaOrderNotificationListener.class);

    @Test
    void the_listener_is_registered_by_default_when_kafka_is_on() {
        runner.withPropertyValues("app.domain-events.kafka.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(KafkaOrderNotificationListener.class));
    }

    @Test
    void the_flag_set_false_removes_the_listener() {
        runner.withPropertyValues(
                        "app.domain-events.kafka.enabled=true",
                        "app.notification.kafka-listeners.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(KafkaOrderNotificationListener.class));
    }

    @Test
    void the_flag_set_true_keeps_the_listener() {
        runner.withPropertyValues(
                        "app.domain-events.kafka.enabled=true",
                        "app.notification.kafka-listeners.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(KafkaOrderNotificationListener.class));
    }

    @Test
    void the_kafka_switch_still_governs_independently() {
        runner.withPropertyValues("app.notification.kafka-listeners.enabled=true")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(KafkaOrderNotificationListener.class));
    }
}
