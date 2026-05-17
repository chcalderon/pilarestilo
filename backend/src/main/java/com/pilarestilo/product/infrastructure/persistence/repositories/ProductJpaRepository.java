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

    /**
     * Reserve stock by incrementing stock_reserved.
     * Succeeds only when available (on_hand - reserved) >= qty.
     */
    @Modifying
    @Query(value = """
      UPDATE product_variants
         SET stock_reserved = stock_reserved + :qty
       WHERE product_id = :productId
         AND lower(trim(color)) = lower(trim(:color))
         AND upper(trim(size)) = upper(trim(:size))
         AND stock_on_hand - stock_reserved >= :qty
      """, nativeQuery = true)
    int atomicReserveVariantStock(@Param("productId") UUID productId,
                                  @Param("color") String color,
                                  @Param("size") String size,
                                  @Param("qty") int qty);

    /**
     * Release a reservation by decrementing stock_reserved.
     * Succeeds only when stock_reserved >= qty.
     */
    @Modifying
    @Query(value = """
      UPDATE product_variants
         SET stock_reserved = stock_reserved - :qty
       WHERE product_id = :productId
         AND lower(trim(color)) = lower(trim(:color))
         AND upper(trim(size)) = upper(trim(:size))
         AND stock_reserved >= :qty
      """, nativeQuery = true)
    int atomicReleaseVariantStock(@Param("productId") UUID productId,
                                   @Param("color") String color,
                                   @Param("size") String size,
                                   @Param("qty") int qty);

    /**
     * Confirm a reservation: decrements both stock_on_hand and stock_reserved.
     * Succeeds only when stock_reserved >= qty.
     */
    @Modifying
    @Query(value = """
      UPDATE product_variants
         SET stock_on_hand = stock_on_hand - :qty,
             stock_reserved = stock_reserved - :qty
       WHERE product_id = :productId
         AND lower(trim(color)) = lower(trim(:color))
         AND upper(trim(size)) = upper(trim(:size))
         AND stock_reserved >= :qty
      """, nativeQuery = true)
    int atomicConfirmVariantStock(@Param("productId") UUID productId,
                                  @Param("color") String color,
                                  @Param("size") String size,
                                  @Param("qty") int qty);
}
