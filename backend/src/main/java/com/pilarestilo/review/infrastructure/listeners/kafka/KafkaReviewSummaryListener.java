package com.pilarestilo.review.infrastructure.listeners.kafka;

import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.review.domain.events.ReviewApproved;
import com.pilarestilo.review.domain.events.ReviewCreated;
import com.pilarestilo.review.domain.events.ReviewDeleted;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaReviewSummaryListener {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;

    public KafkaReviewSummaryListener(ReviewRepository reviewRepository, ProductRepository productRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-review",
            topics = "#{@domainEventTopics.topicFor('ReviewCreated')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReviewCreated(ReviewCreated event) {
        updateSummary(event.productId());
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-review",
            topics = "#{@domainEventTopics.topicFor('ReviewApproved')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReviewApproved(ReviewApproved event) {
        updateSummary(event.productId());
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-review",
            topics = "#{@domainEventTopics.topicFor('ReviewDeleted')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReviewDeleted(ReviewDeleted event) {
        updateSummary(event.productId());
    }

    private void updateSummary(UUID productId) {
        ReviewRepository.RatingSummary summary = reviewRepository.computeSummary(productId);
        BigDecimal avg = summary.avgRating() != null ? summary.avgRating() : BigDecimal.ZERO;
        productRepository.updateRatingSummary(productId, avg, (int) summary.count());
    }
}
