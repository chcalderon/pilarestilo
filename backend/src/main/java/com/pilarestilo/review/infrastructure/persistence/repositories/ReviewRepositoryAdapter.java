package com.pilarestilo.review.infrastructure.persistence.repositories;

import com.pilarestilo.review.domain.model.Review;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import com.pilarestilo.review.infrastructure.persistence.entities.ReviewEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ReviewRepositoryAdapter implements ReviewRepository {

    private final ReviewJpaRepository jpaRepository;

    public ReviewRepositoryAdapter(ReviewJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Review save(Review review) {
        // saveAndFlush, not save: superseding a review writes the old row then inserts the new one,
        // and Hibernate's action queue runs inserts before updates. Left to reorder, the insert
        // would hit uq_review_live_per_user while the previous row was still live. Flushing in call
        // order keeps the "one live review per customer" rule enforceable in the database.
        return toDomain(jpaRepository.saveAndFlush(toEntity(review)));
    }

    @Override
    public Optional<Review> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Review> findByProductId(UUID productId) {
        return jpaRepository.findByProductIdAndSupersededAtIsNull(productId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Review> findApprovedByProductId(UUID productId) {
        return jpaRepository.findByProductIdAndApprovedTrueAndSupersededAtIsNull(productId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Review> findByUserId(UUID userId) {
        return jpaRepository.findByUserIdAndSupersededAtIsNull(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Review> findLiveByProductIdAndUserId(UUID productId, UUID userId) {
        return jpaRepository.findByProductIdAndUserIdAndSupersededAtIsNull(productId, userId).map(this::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<Review> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Review> findByApproved(boolean approved) {
        return jpaRepository.findByApprovedAndSupersededAtIsNull(approved).stream().map(this::toDomain).toList();
    }

    @Override
    public RatingSummary computeSummary(UUID productId) {
        BigDecimal avg = jpaRepository.computeAvgRating(productId);
        long count = jpaRepository.computeCount(productId);
        return new RatingSummary(avg != null ? avg : BigDecimal.ZERO, count);
    }

    private ReviewEntity toEntity(Review review) {
        ReviewEntity e = new ReviewEntity();
        e.setId(review.getId());
        e.setProductId(review.getProductId());
        e.setUserId(review.getUserId());
        e.setRating((short) review.getRating());
        e.setTitle(review.getTitle());
        e.setComment(review.getComment());
        e.setApproved(review.isApproved());
        e.setCreatedAt(review.getCreatedAt());
        e.setSupersededAt(review.getSupersededAt());
        return e;
    }

    private Review toDomain(ReviewEntity e) {
        Review review = Review.create(e.getProductId(), e.getUserId(), e.getRating(), e.getTitle(), e.getComment());
        review.setId(e.getId());
        review.setApproved(e.isApproved());
        review.setCreatedAt(e.getCreatedAt());
        review.setSupersededAt(e.getSupersededAt());
        return review;
    }
}
