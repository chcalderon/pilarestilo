package com.pilarestilo.product.infrastructure.persistence.repositories;

import com.pilarestilo.product.infrastructure.persistence.entities.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID>,
        JpaSpecificationExecutor<ProductEntity> {

    @Modifying
    @Query("UPDATE ProductEntity p SET p.avgRating = :avgRating, p.reviewCount = :reviewCount WHERE p.id = :productId")
    void updateRatingSummary(@Param("productId") UUID productId,
                             @Param("avgRating") BigDecimal avgRating,
                             @Param("reviewCount") int reviewCount);

    long countByCategoriesId(UUID categoryId);
}
