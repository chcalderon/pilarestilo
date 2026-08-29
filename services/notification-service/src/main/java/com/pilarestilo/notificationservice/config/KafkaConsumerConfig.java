package com.pilarestilo.notificationservice.config;

import com.pilarestilo.notificationservice.events.DomainEventTopics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

/**
 * Kafka consumer stack, ported from the monolith's {@code KafkaDomainEventsConfiguration}.
 *
 * <p>Difference: type-info headers are <b>ignored</b> ({@code USE_TYPE_INFO_HEADERS=false}). The
 * monolith's publisher stamps {@code __TypeId__} with its own FQNs; this service declares its own
 * thin event records under {@code events/} and lets the {@code @KafkaListener} method parameter type
 * drive deserialization via {@link ByteArrayJacksonJsonMessageConverter}. That keeps the service
 * free of the monolith's event classes.
 *
 * <p>Consumer group {@code pe-notification-service}, distinct from the backend's own group, so
 * before the cutover both can consume without stealing each other's offsets. DLT + retry are the
 * shared convention: {@code <topic>.dlt}, 3 attempts, 1500&nbsp;ms backoff.
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaDomainEventsProperties.class)
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, byte[]> domainEventsConsumerFactory(
            KafkaProperties kafkaProperties,
            KafkaDomainEventsProperties domainEventsProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, domainEventsProperties.getConsumerGroupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        // `latest`, not `earliest`: this service must never replay the topic's retained history —
        // that would re-send a confirmation for every order in the last 7 days. At the cutover the
        // group's offsets are pre-seeded to the log-end while the monolith still consumes, so this
        // reset only ever applies if that step is skipped; even then, forward-only is the safe default.
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /** Only used by the dead-letter recoverer to republish poison messages. */
    @Bean
    public ProducerFactory<String, byte[]> domainEventsDltProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, byte[]> domainEventsDltKafkaTemplate(
            ProducerFactory<String, byte[]> domainEventsDltProducerFactory) {
        return new KafkaTemplate<>(domainEventsDltProducerFactory);
    }

    @Bean(name = "domainEventsKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> domainEventsKafkaListenerContainerFactory(
            ConsumerFactory<String, byte[]> domainEventsConsumerFactory,
            KafkaTemplate<String, byte[]> domainEventsDltKafkaTemplate,
            KafkaDomainEventsProperties domainEventsProperties,
            DomainEventTopics domainEventTopics) {

        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(domainEventsConsumerFactory);
        factory.setRecordMessageConverter(new ByteArrayJacksonJsonMessageConverter());

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                domainEventsDltKafkaTemplate,
                (consumerRecord, ex) -> new TopicPartition(
                        domainEventTopics.deadLetterTopicFor(consumerRecord.topic()), consumerRecord.partition()));

        long retryAttempts = Math.max(1, domainEventsProperties.getRetryMaxAttempts());
        long retryBackoff = Math.max(250, domainEventsProperties.getRetryBackoffMs());
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(retryBackoff, retryAttempts - 1));
        errorHandler.setCommitRecovered(true);

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
