package com.pilarestilo.discount.infrastructure.persistence.repositories;

import com.pilarestilo.discount.infrastructure.persistence.entities.DiscountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscountJpaRepository extends JpaRepository<DiscountEntity, UUID> {

    Optional<DiscountEntity> findByCode(String code);

    @Query("SELECT d FROM DiscountEntity d WHERE d.active = true AND d.validUntil >= :today ORDER BY d.validUntil ASC")
    List<DiscountEntity> findActiveDiscounts(@Param("today") LocalDate today);

    @Query("SELECT d FROM DiscountEntity d WHERE d.active = false OR d.validUntil < :today ORDER BY d.validUntil DESC")
    List<DiscountEntity> findExpiredDiscounts(@Param("today") LocalDate today);

    @Query("SELECT COUNT(d) FROM DiscountEntity d WHERE d.code LIKE :pattern")
    long countByCodeLike(@Param("pattern") String pattern);
}
