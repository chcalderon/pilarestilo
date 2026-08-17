package com.pilarestilo.inventoryservice.persistence;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID>, JpaSpecificationExecutor<ProductEntity> {

    @Query(value = """
            select exists(
                select 1
                  from product_variants
                 where product_id = :productId
            )
            """, nativeQuery = true)
    boolean hasVariants(@Param("productId") UUID productId);

    @Modifying
    @Query("""
            update ProductEntity p
               set p.stock = p.stock - :qty,
                   p.updatedAt = :updatedAt
             where p.id = :productId
               and p.stock >= :qty
            """)
    int reserveStock(@Param("productId") UUID productId,
                     @Param("qty") int qty,
                     @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Query(value = """
            update product_variants
               set stock_reserved = stock_reserved + :qty
             where product_id = :productId
               and lower(trim(color)) = lower(trim(:color))
               and upper(trim(size)) = upper(trim(:size))
               and stock_on_hand - stock_reserved >= :qty
            """, nativeQuery = true)
    int reserveVariantStock(@Param("productId") UUID productId,
                            @Param("color") String color,
                            @Param("size") String size,
                            @Param("qty") int qty);

    @Modifying
    @Query(value = """
            update products p
               set stock = coalesce((
                       select sum(v.stock_on_hand - v.stock_reserved)
                         from product_variants v
                        where v.product_id = :productId
                   ), 0),
                   updated_at = :updatedAt
             where p.id = :productId
            """, nativeQuery = true)
    int syncProductStockFromVariants(@Param("productId") UUID productId,
                                     @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Query("""
            update ProductEntity p
               set p.stock = p.stock + :qty,
                   p.updatedAt = :updatedAt
             where p.id = :productId
            """)
    int releaseStock(@Param("productId") UUID productId,
                     @Param("qty") int qty,
                     @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Query(value = """
            update product_variants
               set stock_reserved = stock_reserved - :qty
             where product_id = :productId
               and lower(trim(color)) = lower(trim(:color))
               and upper(trim(size)) = upper(trim(:size))
               and stock_reserved >= :qty
            """, nativeQuery = true)
    int releaseVariantStock(@Param("productId") UUID productId,
                            @Param("color") String color,
                            @Param("size") String size,
                            @Param("qty") int qty);

    /**
     * Turns a reservation into a sale: the units leave the shelf and stop being held.
     *
     * <p>Reserving only moves stock into stock_reserved, so without this the units are held for a
     * completed order forever and stock_on_hand still counts them as sellable. Mirrors
     * atomicConfirmVariantStock in the monolith, which is the other writer of this table.
     */
    @Modifying
    @Query(value = """
            update product_variants
               set stock_on_hand = stock_on_hand - :qty,
                   stock_reserved = stock_reserved - :qty
             where product_id = :productId
               and lower(trim(color)) = lower(trim(:color))
               and upper(trim(size)) = upper(trim(:size))
               and stock_reserved >= :qty
               and stock_on_hand >= :qty
            """, nativeQuery = true)
    int confirmVariantStock(@Param("productId") UUID productId,
                            @Param("color") String color,
                            @Param("size") String size,
                            @Param("qty") int qty);

    /**
     * Puts confirmed units back on the shelf when a paid sale is undone.
     *
     * <p>The inverse of {@link #confirmVariantStock}, not of the reservation: by then the units are
     * out of both columns, so only stock_on_hand goes back up. Releasing instead would decrement a
     * reservation that no longer exists. Mirrors atomicReturnVariantStock in the monolith, which is
     * the other writer of this table.
     */
    @Modifying
    @Query(value = """
            update product_variants
               set stock_on_hand = stock_on_hand + :qty
             where product_id = :productId
               and lower(trim(color)) = lower(trim(:color))
               and upper(trim(size)) = upper(trim(:size))
            """, nativeQuery = true)
    int returnVariantStock(@Param("productId") UUID productId,
                           @Param("color") String color,
                           @Param("size") String size,
                           @Param("qty") int qty);
}
