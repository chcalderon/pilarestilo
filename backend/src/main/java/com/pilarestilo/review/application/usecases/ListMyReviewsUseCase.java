package com.pilarestilo.review.application.usecases;

import com.pilarestilo.review.application.dto.ReviewDto;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ListMyReviewsUseCase {

    private final ReviewRepository reviewRepository;

    public ListMyReviewsUseCase(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public List<ReviewDto> execute(UUID userId) {
        return reviewRepository.findByUserId(userId)
                .stream()
                .map(ReviewDto::from)
                .toList();
    }
}
