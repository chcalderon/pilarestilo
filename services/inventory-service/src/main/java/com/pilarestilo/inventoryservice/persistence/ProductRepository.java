package com.pilarestilo.inventoryservice.persistence;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID>, JpaSpecificationExecutor<ProductEntity> {

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
    @Query("""
            update ProductEntity p
               set p.stock = p.stock + :qty,
                   p.updatedAt = :updatedAt
             where p.id = :productId
            """)
    int releaseStock(@Param("productId") UUID productId,
                     @Param("qty") int qty,
                     @Param("updatedAt") Instant updatedAt);
}
