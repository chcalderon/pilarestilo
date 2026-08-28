package com.pilarestilo.notificationservice.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventTopicsTest {

    private final DomainEventTopics topics = new DomainEventTopics("pe.domain", ".dlt");

    @Test
    void maps_an_event_name_to_the_monolith_topic_convention() {
        assertThat(topics.topicFor("OrderCreated")).isEqualTo("pe.domain.order-created");
        assertThat(topics.topicFor("PaymentRegistered")).isEqualTo("pe.domain.payment-registered");
        assertThat(topics.topicFor("SalesDocumentIssued")).isEqualTo("pe.domain.sales-document-issued");
        assertThat(topics.topicFor("DiscountCodeAssigned")).isEqualTo("pe.domain.discount-code-assigned");
    }

    @Test
    void appends_the_dlt_suffix() {
        assertThat(topics.deadLetterTopicFor("pe.domain.order-created"))
                .isEqualTo("pe.domain.order-created.dlt");
    }
}
