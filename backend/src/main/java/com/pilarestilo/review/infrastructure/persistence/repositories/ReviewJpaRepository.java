package com.pilarestilo.review.infrastructure.persistence.repositories;

import com.pilarestilo.review.infrastructure.persistence.entities.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewJpaRepository extends JpaRepository<ReviewEntity, UUID> {

    List<ReviewEntity> findByProductIdAndSupersededAtIsNull(UUID productId);

    List<ReviewEntity> findByProductIdAndApprovedTrueAndSupersededAtIsNull(UUID productId);

    List<ReviewEntity> findByUserIdAndSupersededAtIsNull(UUID userId);

    Optional<ReviewEntity> findByProductIdAndUserIdAndSupersededAtIsNull(UUID productId, UUID userId);

    List<ReviewEntity> findByApprovedAndSupersededAtIsNull(boolean approved);

    @Query("SELECT AVG(r.rating) FROM ReviewEntity r WHERE r.productId = :productId AND r.approved = true")
    BigDecimal computeAvgRating(@Param("productId") UUID productId);

    @Query("SELECT COUNT(r) FROM ReviewEntity r WHERE r.productId = :productId AND r.approved = true")
    long computeCount(@Param("productId") UUID productId);
}
