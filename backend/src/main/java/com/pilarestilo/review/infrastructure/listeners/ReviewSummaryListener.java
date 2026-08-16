package com.pilarestilo.review.infrastructure.listeners;

import com.pilarestilo.review.application.usecases.RefreshProductRatingUseCase;
import com.pilarestilo.review.domain.events.ReviewApproved;
import com.pilarestilo.review.domain.events.ReviewCreated;
import com.pilarestilo.review.domain.events.ReviewDeleted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** In-process transport for {@link RefreshProductRatingUseCase}. Carries no behaviour. */
@Component
public class ReviewSummaryListener {

    private final RefreshProductRatingUseCase refreshProductRating;

    public ReviewSummaryListener(RefreshProductRatingUseCase refreshProductRating) {
        this.refreshProductRating = refreshProductRating;
    }

    @EventListener
    public void onReviewCreated(ReviewCreated event) {
        refreshProductRating.execute(event.productId());
    }

    @EventListener
    public void onReviewApproved(ReviewApproved event) {
        refreshProductRating.execute(event.productId());
    }

    @EventListener
    public void onReviewDeleted(ReviewDeleted event) {
        refreshProductRating.execute(event.productId());
    }
}
