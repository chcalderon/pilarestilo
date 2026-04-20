package com.pilarestilo.customercredit.infrastructure.persistence.repositories;

import com.pilarestilo.customercredit.infrastructure.persistence.entities.CreditMovementEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CreditMovementJpaRepository extends JpaRepository<CreditMovementEntity, UUID> {

    Page<CreditMovementEntity> findByCustomerId(UUID customerId, Pageable pageable);
}
