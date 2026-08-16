package com.pilarestilo.review.application.usecases;

import com.pilarestilo.review.application.dto.ReviewDto;
import com.pilarestilo.review.domain.model.Review;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ListReviewsForProductUseCase {

    private final ReviewRepository reviewRepository;

    public ListReviewsForProductUseCase(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    /**
     * The live review of every customer who reviewed this product — one each, the latest they
     * wrote — plus the viewer's own live review even when it is still awaiting approval.
     *
     * <p>Without that last part, replacing a one-tap rating with a written review would make the
     * customer's own opinion vanish from the page until a moderator got to it, which reads as the
     * review having been lost.
     *
     * @param viewerId the customer reading the page, or null when nobody is signed in
     */
    public List<ReviewDto> execute(UUID productId, UUID viewerId) {
        List<Review> visible = new ArrayList<>(reviewRepository.findApprovedByProductId(productId));
        if (viewerId != null) {
            reviewRepository.findLiveByProductIdAndUserId(productId, viewerId)
                    .filter(mine -> visible.stream().noneMatch(shown -> shown.getId().equals(mine.getId())))
                    .ifPresent(visible::add);
        }
        return visible.stream().map(ReviewDto::from).toList();
    }
}
