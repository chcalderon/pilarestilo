package com.pilarestilo.review.application.dto;

import com.pilarestilo.review.domain.model.Review;

import java.time.Instant;
import java.util.UUID;

public record ReviewDto(
        UUID id,
        UUID productId,
        UUID userId,
        int rating,
        String title,
        String comment,
        boolean approved,
        Instant createdAt
) {
    public static ReviewDto from(Review review) {
        return new ReviewDto(
                review.getId(),
                review.getProductId(),
                review.getUserId(),
                review.getRating(),
                review.getTitle(),
                review.getComment(),
                review.isApproved(),
                review.getCreatedAt()
        );
    }
}
