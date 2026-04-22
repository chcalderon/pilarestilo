package com.pilarestilo.orderservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

    @Modifying
    @Query(value = """
            update products
               set stock = stock - :qty,
                   updated_at = :updatedAt
             where id = :productId
               and stock >= :qty
            """, nativeQuery = true)
    int reserveStockAndTouch(
            @Param("productId") UUID productId,
            @Param("qty") int qty,
            @Param("updatedAt") Instant updatedAt
    );
}
