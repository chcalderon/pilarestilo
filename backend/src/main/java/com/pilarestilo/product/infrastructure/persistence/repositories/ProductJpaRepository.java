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

    /**
     * Recomputes products.stock from the variant rows.
     *
     * <p>products.stock is a cache of a value product_variants already holds — Product derives it
     * the same way in memory. Only inventory-service kept the stored copy current, so every
     * reservation taken locally left it frozen. It gates the buy button on the product page
     * (`stock <= 0` blocks the whole product), so drifting to zero made a product with stock
     * unbuyable, with nothing in the variants table to explain why.
     */
    @Modifying
    @Query(value = """
      UPDATE products p
         SET stock = COALESCE((
                 SELECT SUM(v.stock_on_hand - v.stock_reserved)
                   FROM product_variants v
                  WHERE v.product_id = p.id), p.stock)
       WHERE p.id = :productId
         AND EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id)
      """, nativeQuery = true)
    int syncProductStockFromVariants(@Param("productId") UUID productId);
}
