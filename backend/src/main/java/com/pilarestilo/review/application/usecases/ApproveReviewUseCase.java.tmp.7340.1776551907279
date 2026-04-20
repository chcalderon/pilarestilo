package com.pilarestilo.review.application.usecases;

import com.pilarestilo.review.application.dto.ReviewDto;
import com.pilarestilo.review.domain.events.ReviewApproved;
import com.pilarestilo.review.domain.model.Review;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ApproveReviewUseCase {

    private final ReviewRepository reviewRepository;
    private final DomainEventPublisher eventPublisher;

    public ApproveReviewUseCase(ReviewRepository reviewRepository, DomainEventPublisher eventPublisher) {
        this.reviewRepository = reviewRepository;
        this.eventPublisher = eventPublisher;
    }

    public ReviewDto execute(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new DomainException("Review not found: " + reviewId));
        review.approve();
        Review saved = reviewRepository.save(review);
        eventPublisher.publish(new ReviewApproved(reviewId, review.getProductId()));
        return ReviewDto.from(saved);
    }
}
