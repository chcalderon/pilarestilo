package com.pilarestilo.review.application.usecases;

import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.review.application.dto.ReviewDto;
import com.pilarestilo.review.domain.events.ReviewCreated;
import com.pilarestilo.review.domain.model.Review;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

@Service
public class CreateReviewUseCase {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final DomainEventPublisher eventPublisher;

    public CreateReviewUseCase(ReviewRepository reviewRepository,
                               ProductRepository productRepository,
                               DomainEventPublisher eventPublisher) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    // Superseding the previous review and inserting the new one are one change: a crash between
    // them would leave the customer with no live review at all.
    @Transactional
    public ReviewDto execute(UUID productId, UUID userId, int rating, String title, String comment) {
        if (productRepository.findById(productId).isEmpty()) {
            throw new DomainException("Product not found: " + productId);
        }
        // A second review replaces the first rather than being refused. Somebody who rated in one
        // tap from a product card can come back and say why, and somebody who wore the thing twice
        // can revise what they said. The previous review is kept, not overwritten: what they
        // thought before is a fact, and only the live row counts towards the product's rating.
        reviewRepository.findLiveByProductIdAndUserId(productId, userId)
                .ifPresent(previous -> {
                    previous.supersede(Instant.now());
                    reviewRepository.save(previous);
                });
        Review review = Review.create(productId, userId, rating, title, comment);
        // Quick rating flow from product cards: rating-only submissions are auto-approved.
        if (isBlank(title) && isBlank(comment)) {
            review.approve();
        }
        Review saved = reviewRepository.save(review);
        // Published after the commit, not inside it. The summary listener recomputes the product's
        // rating by querying the reviews table, and the publisher hands the event over immediately:
        // announced from inside the transaction, the listener reads a table that does not yet
        // contain this review and writes a rating of zero over a correct one.
        publishAfterCommit(new ReviewCreated(saved.getId(), productId, userId, rating));
        return ReviewDto.from(saved);
    }

    private void publishAfterCommit(ReviewCreated event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publish(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publish(event);
            }
        });
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
