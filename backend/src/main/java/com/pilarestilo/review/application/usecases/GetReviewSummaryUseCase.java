package com.pilarestilo.review.application.usecases;

import com.pilarestilo.review.application.dto.ReviewSummaryDto;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GetReviewSummaryUseCase {

    private final ReviewRepository reviewRepository;

    public GetReviewSummaryUseCase(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public ReviewSummaryDto execute(UUID productId) {
        ReviewRepository.RatingSummary summary = reviewRepository.computeSummary(productId);
        return new ReviewSummaryDto(productId, summary.avgRating(), summary.count());
    }
}
