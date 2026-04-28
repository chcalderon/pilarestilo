package com.pilarestilo.cashregister.infrastructure.persistence.repositories;

import com.pilarestilo.cashregister.domain.enums.CashRegisterStatus;
import com.pilarestilo.cashregister.infrastructure.persistence.entities.CashRegisterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CashRegisterJpaRepository extends JpaRepository<CashRegisterEntity, UUID> {
    Optional<CashRegisterEntity> findBySellerIdAndStatus(UUID sellerId, CashRegisterStatus status);

    Optional<CashRegisterEntity> findFirstByStatusOrderByOpenedAtDesc(CashRegisterStatus status);
}
