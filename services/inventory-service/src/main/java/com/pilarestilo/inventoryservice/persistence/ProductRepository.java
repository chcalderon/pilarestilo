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
               set stock = stock - :qty
             where product_id = :productId
               and lower(trim(color)) = lower(trim(:color))
               and upper(trim(size)) = upper(trim(:size))
               and stock >= :qty
            """, nativeQuery = true)
    int reserveVariantStock(@Param("productId") UUID productId,
                            @Param("color") String color,
                            @Param("size") String size,
                            @Param("qty") int qty);

    @Modifying
    @Query(value = """
            update product_size_stocks
               set stock = stock - :qty
             where product_id = :productId
               and upper(trim(size)) = upper(trim(:size))
               and stock >= :qty
            """, nativeQuery = true)
    int reserveSizeStock(@Param("productId") UUID productId,
                         @Param("size") String size,
                         @Param("qty") int qty);

    @Modifying
    @Query(value = """
            update products p
               set stock = coalesce((
                       select sum(v.stock)
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
               set stock = stock + :qty
             where product_id = :productId
               and lower(trim(color)) = lower(trim(:color))
               and upper(trim(size)) = upper(trim(:size))
            """, nativeQuery = true)
    int releaseVariantStock(@Param("productId") UUID productId,
                            @Param("color") String color,
                            @Param("size") String size,
                            @Param("qty") int qty);

    @Modifying
    @Query(value = """
            update product_size_stocks
               set stock = stock + :qty
             where product_id = :productId
               and upper(trim(size)) = upper(trim(:size))
            """, nativeQuery = true)
    int releaseSizeStock(@Param("productId") UUID productId,
                         @Param("size") String size,
                         @Param("qty") int qty);
}
