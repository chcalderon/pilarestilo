package com.pilarestilo.review.infrastructure.listeners;

import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.review.domain.events.ReviewApproved;
import com.pilarestilo.review.domain.events.ReviewCreated;
import com.pilarestilo.review.domain.events.ReviewDeleted;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class ReviewSummaryListener {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;

    public ReviewSummaryListener(ReviewRepository reviewRepository, ProductRepository productRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReviewCreated(ReviewCreated event) {
        updateSummary(event.productId());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReviewApproved(ReviewApproved event) {
        updateSummary(event.productId());
    }

    @EventListener
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
