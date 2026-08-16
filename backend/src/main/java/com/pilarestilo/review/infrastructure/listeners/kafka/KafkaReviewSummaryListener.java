package com.pilarestilo.review.infrastructure.listeners.kafka;

import com.pilarestilo.review.application.usecases.RefreshProductRatingUseCase;
import com.pilarestilo.review.domain.events.ReviewApproved;
import com.pilarestilo.review.domain.events.ReviewCreated;
import com.pilarestilo.review.domain.events.ReviewDeleted;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka transport for {@link RefreshProductRatingUseCase}. Carries no behaviour.
 *
 * <p>It used to hold its own copy of the refresh, identical to the in-process one and with nothing
 * keeping them identical.
 */
@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaReviewSummaryListener {

    private static final String GROUP =
            "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-review";

    private final RefreshProductRatingUseCase refreshProductRating;

    public KafkaReviewSummaryListener(RefreshProductRatingUseCase refreshProductRating) {
        this.refreshProductRating = refreshProductRating;
    }

    @KafkaListener(
            groupId = GROUP,
            topics = "#{@domainEventTopics.topicFor('ReviewCreated')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onReviewCreated(ReviewCreated event) {
        refreshProductRating.execute(event.productId());
    }

    @KafkaListener(
            groupId = GROUP,
            topics = "#{@domainEventTopics.topicFor('ReviewApproved')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onReviewApproved(ReviewApproved event) {
        refreshProductRating.execute(event.productId());
    }

    @KafkaListener(
            groupId = GROUP,
            topics = "#{@domainEventTopics.topicFor('ReviewDeleted')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onReviewDeleted(ReviewDeleted event) {
        refreshProductRating.execute(event.productId());
    }
}
