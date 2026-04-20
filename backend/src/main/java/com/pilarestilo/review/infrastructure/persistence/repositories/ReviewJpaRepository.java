package com.pilarestilo.review.infrastructure.persistence.repositories;

import com.pilarestilo.review.infrastructure.persistence.entities.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ReviewJpaRepository extends JpaRepository<ReviewEntity, UUID> {

    List<ReviewEntity> findByProductId(UUID productId);

    List<ReviewEntity> findByProductIdAndApprovedTrue(UUID productId);

    List<ReviewEntity> findByUserId(UUID userId);

    boolean existsByProductIdAndUserId(UUID productId, UUID userId);

    List<ReviewEntity> findByApproved(boolean approved);

    @Query("SELECT AVG(r.rating) FROM ReviewEntity r WHERE r.productId = :productId AND r.approved = true")
    BigDecimal computeAvgRating(@Param("productId") UUID productId);

    @Query("SELECT COUNT(r) FROM ReviewEntity r WHERE r.productId = :productId AND r.approved = true")
    long computeCount(@Param("productId") UUID productId);
}
