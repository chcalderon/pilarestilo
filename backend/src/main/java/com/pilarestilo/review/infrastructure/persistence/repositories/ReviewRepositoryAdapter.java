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
        return toDomain(jpaRepository.save(toEntity(review)));
    }

    @Override
    public Optional<Review> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Review> findByProductId(UUID productId) {
        return jpaRepository.findByProductId(productId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Review> findApprovedByProductId(UUID productId) {
        return jpaRepository.findByProductIdAndApprovedTrue(productId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Review> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByProductIdAndUserId(UUID productId, UUID userId) {
        return jpaRepository.existsByProductIdAndUserId(productId, userId);
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
        return jpaRepository.findByApproved(approved).stream().map(this::toDomain).toList();
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
        return e;
    }

    private Review toDomain(ReviewEntity e) {
        Review review = Review.create(e.getProductId(), e.getUserId(), e.getRating(), e.getTitle(), e.getComment());
        review.setId(e.getId());
        review.setApproved(e.isApproved());
        review.setCreatedAt(e.getCreatedAt());
        return review;
    }
}
