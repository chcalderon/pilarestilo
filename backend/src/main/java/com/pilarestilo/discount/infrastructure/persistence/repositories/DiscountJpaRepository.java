package com.pilarestilo.discount.infrastructure.persistence.repositories;

import com.pilarestilo.discount.infrastructure.persistence.entities.DiscountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DiscountJpaRepository extends JpaRepository<DiscountEntity, UUID> {
    Optional<DiscountEntity> findByCode(String code);
}
