package com.pilarestilo.customercredit.infrastructure.persistence.repositories;

import com.pilarestilo.customercredit.domain.model.CreditMovement;
import com.pilarestilo.customercredit.domain.ports.CreditMovementRepository;
import com.pilarestilo.customercredit.infrastructure.persistence.entities.CreditMovementEntity;
import com.pilarestilo.shared.application.Money;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CreditMovementRepositoryAdapter implements CreditMovementRepository {

    private final CreditMovementJpaRepository jpaRepository;

    public CreditMovementRepositoryAdapter(CreditMovementJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CreditMovement save(CreditMovement movement) {
        CreditMovementEntity entity = toEntity(movement);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Page<CreditMovement> findByCustomerId(UUID customerId, Pageable pageable) {
        return jpaRepository.findByCustomerId(customerId, pageable).map(this::toDomain);
    }

    private CreditMovementEntity toEntity(CreditMovement m) {
        CreditMovementEntity e = new CreditMovementEntity();
        e.setId(m.getId());
        e.setCustomerId(m.getCustomerId());
        e.setType(m.getType());
        e.setAmountValue(m.getAmount().amount());
        e.setAmountCurrency(m.getAmount().currency());
        e.setReason(m.getReason());
        e.setCreatedAt(m.getCreatedAt());
        return e;
    }

    private CreditMovement toDomain(CreditMovementEntity e) {
        return CreditMovement.reconstruct(
                e.getId(), e.getCustomerId(), e.getType(),
                new Money(e.getAmountValue(), e.getAmountCurrency()),
                e.getReason(), e.getCreatedAt()
        );
    }
}
