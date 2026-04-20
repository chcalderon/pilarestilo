package com.pilarestilo.review.application.usecases;

import com.pilarestilo.review.application.dto.ReviewDto;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ListReviewsForProductUseCase {

    private final ReviewRepository reviewRepository;

    public ListReviewsForProductUseCase(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public List<ReviewDto> execute(UUID productId) {
        return reviewRepository.findApprovedByProductId(productId)
                .stream()
                .map(ReviewDto::from)
                .toList();
    }
}
