package com.pilarestilo.review.application.usecases;

import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Brings a product's stored rating back in line with its live reviews.
 *
 * <p>The body of this lived twice, copied verbatim into the in-process listener and its Kafka twin.
 * Nothing made them agree, which is the shape every drift defect in this codebase has taken. It is
 * one implementation now and the listeners are transports.
 *
 * <p>Recomputes rather than increments on purpose: a counter that is added to has no way back once
 * it is wrong, while a recount is self-healing and costs one aggregate query on an event that
 * happens when somebody writes a review — rare, and never in a request the customer waits on.
 *
 * <p>products.avg_rating and review_count stay stored rather than derived at read time, and that is
 * deliberate: product-service serves catalogue reads and holds no review code, so deriving would
 * mean it queries a table outside its domain on every listing. A stored value refreshed from one
 * place is the cheaper trade.
 */
@Service
public class RefreshProductRatingUseCase {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;

    public RefreshProductRatingUseCase(ReviewRepository reviewRepository,
                                       ProductRepository productRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
    }

    /**
     * Runs in its own transaction so a failure to refresh a rating cannot roll back the review that
     * triggered it. The rating is a summary; the review is the fact.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(UUID productId) {
        ReviewRepository.RatingSummary summary = reviewRepository.computeSummary(productId);
        BigDecimal average = summary.avgRating() != null ? summary.avgRating() : BigDecimal.ZERO;
        productRepository.updateRatingSummary(productId, average, (int) summary.count());
    }
}
