package com.pilarestilo.cashregister.infrastructure.persistence.repositories;

import com.pilarestilo.cashregister.infrastructure.persistence.entities.CashMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CashMovementJpaRepository extends JpaRepository<CashMovementEntity, UUID> {
    List<CashMovementEntity> findByCashRegisterId(UUID cashRegisterId);
}
