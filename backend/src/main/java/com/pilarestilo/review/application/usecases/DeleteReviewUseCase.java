package com.pilarestilo.review.application.usecases;

import com.pilarestilo.review.domain.events.ReviewDeleted;
import com.pilarestilo.review.domain.model.Review;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeleteReviewUseCase {

    private final ReviewRepository reviewRepository;
    private final DomainEventPublisher eventPublisher;

    public DeleteReviewUseCase(ReviewRepository reviewRepository, DomainEventPublisher eventPublisher) {
        this.reviewRepository = reviewRepository;
        this.eventPublisher = eventPublisher;
    }

    public void execute(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new DomainException("Review not found: " + reviewId));
        reviewRepository.deleteById(reviewId);
        eventPublisher.publish(new ReviewDeleted(reviewId, review.getProductId()));
    }
}
