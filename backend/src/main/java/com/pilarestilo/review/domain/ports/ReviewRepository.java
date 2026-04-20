package com.pilarestilo.review.domain.ports;

import com.pilarestilo.review.domain.model.Review;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository {

    Review save(Review review);

    Optional<Review> findById(UUID id);

    List<Review> findByProductId(UUID productId);

    List<Review> findApprovedByProductId(UUID productId);

    List<Review> findByUserId(UUID userId);

    boolean existsByProductIdAndUserId(UUID productId, UUID userId);

    void deleteById(UUID id);

    List<Review> findAll();

    List<Review> findByApproved(boolean approved);

    record RatingSummary(BigDecimal avgRating, long count) {}

    RatingSummary computeSummary(UUID productId);
}
