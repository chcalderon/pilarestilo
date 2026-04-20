package com.pilarestilo.review.application.usecases;

import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.review.application.dto.ReviewDto;
import com.pilarestilo.review.domain.events.ReviewCreated;
import com.pilarestilo.review.domain.model.Review;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.springframework.stereotype.Service;

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

    public ReviewDto execute(UUID productId, UUID userId, int rating, String title, String comment) {
        if (productRepository.findById(productId).isEmpty()) {
            throw new DomainException("Product not found: " + productId);
        }
        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            throw new DomainException("User has already reviewed this product");
        }
        Review review = Review.create(productId, userId, rating, title, comment);
        Review saved = reviewRepository.save(review);
        eventPublisher.publish(new ReviewCreated(saved.getId(), productId, userId, rating));
        return ReviewDto.from(saved);
    }
}
