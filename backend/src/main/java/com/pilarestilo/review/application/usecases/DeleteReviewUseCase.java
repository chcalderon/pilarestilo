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

    /**
     * @param requesterId  who is asking
     * @param isModerator  whether they hold reviews.moderate
     * @throws DomainException when somebody tries to delete a review that is not theirs
     */
    public void execute(UUID reviewId, UUID requesterId, boolean isModerator) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new DomainException("Review not found: " + reviewId));
        // The endpoint used to require nothing but a session, so any signed-in customer could
        // delete anybody's review. Ownership is checked here rather than in the controller because
        // it is a rule about reviews, not about HTTP.
        if (!isModerator && !review.getUserId().equals(requesterId)) {
            throw new DomainException("A review can only be deleted by its author or a moderator");
        }
        reviewRepository.deleteById(reviewId);
        eventPublisher.publish(new ReviewDeleted(reviewId, review.getProductId()));
    }
}
