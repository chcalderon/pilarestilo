package com.pilarestilo.discount.infrastructure.persistence.repositories;

import com.pilarestilo.discount.infrastructure.persistence.entities.DiscountCodeUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DiscountCodeUsageJpaRepository extends JpaRepository<DiscountCodeUsageEntity, UUID> {
    boolean existsByDiscountIdAndUserId(UUID discountId, UUID userId);
}
