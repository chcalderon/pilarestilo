package com.pilarestilo.cashregister.infrastructure.persistence.repositories;

import com.pilarestilo.cashregister.domain.model.CashMovement;
import com.pilarestilo.cashregister.domain.ports.CashMovementRepository;
import com.pilarestilo.cashregister.infrastructure.persistence.entities.CashMovementEntity;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

@Component
public class CashMovementRepositoryAdapter implements CashMovementRepository {

    private final CashMovementJpaRepository jpaRepository;

    public CashMovementRepositoryAdapter(CashMovementJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CashMovement save(CashMovement m) {
        return toDomain(jpaRepository.save(toEntity(m)));
    }

    @Override
    public List<CashMovement> findByCashRegisterId(UUID cashRegisterId) {
        return jpaRepository.findByCashRegisterId(cashRegisterId).stream().map(this::toDomain).toList();
    }

    private CashMovementEntity toEntity(CashMovement m) {
        CashMovementEntity e = new CashMovementEntity();
        e.setId(m.getId());
        e.setCashRegisterId(m.getCashRegisterId());
        e.setType(m.getType());
        e.setAmount(m.getAmount());
        e.setDescription(m.getDescription());
        e.setOrderId(m.getOrderId());
        e.setRecordedAt(m.getRecordedAt());
        e.setRecordedBy(m.getRecordedBy());
        return e;
    }

    private CashMovement toDomain(CashMovementEntity e) {
        return CashMovement.reconstruct(e.getId(), e.getCashRegisterId(), e.getType(),
                e.getAmount(), e.getDescription(), e.getOrderId(),
                e.getRecordedAt(), e.getRecordedBy());
    }
}
