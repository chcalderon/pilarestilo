package com.pilarestilo.cashregister.infrastructure.persistence.repositories;

import com.pilarestilo.cashregister.domain.enums.CashRegisterStatus;
import com.pilarestilo.cashregister.domain.model.CashMovement;
import com.pilarestilo.cashregister.domain.model.CashRegister;
import com.pilarestilo.cashregister.domain.ports.CashMovementRepository;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import com.pilarestilo.cashregister.infrastructure.persistence.entities.CashRegisterEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CashRegisterRepositoryAdapter implements CashRegisterRepository {

    private final CashRegisterJpaRepository jpaRepository;
    private final CashMovementRepository movementRepository;

    public CashRegisterRepositoryAdapter(CashRegisterJpaRepository jpaRepository,
                                          CashMovementRepository movementRepository) {
        this.jpaRepository = jpaRepository;
        this.movementRepository = movementRepository;
    }

    @Override
    public CashRegister save(CashRegister cr) {
        jpaRepository.save(applyToEntity(cr, jpaRepository.findById(cr.getId())
                .orElseGet(CashRegisterEntity::new)));
        for (CashMovement m : cr.getMovements()) {
            movementRepository.save(m);
        }
        return findById(cr.getId()).orElseThrow();
    }

    @Override
    public Optional<CashRegister> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<CashRegister> findOpenBySellerId(UUID sellerId) {
        return jpaRepository.findBySellerIdAndStatus(sellerId, CashRegisterStatus.OPEN).map(this::toDomain);
    }

    @Override
    public Optional<CashRegister> findAnyOpenRegister() {
        return jpaRepository.findFirstByStatusOrderByOpenedAtDesc(CashRegisterStatus.OPEN).map(this::toDomain);
    }

    @Override
    public Page<CashRegister> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public Page<CashRegister> findHistoryForSeller(UUID sellerId,
                                                   CashRegisterStatus status,
                                                   LocalDateTime openedFrom,
                                                   LocalDateTime openedTo,
                                                   Pageable pageable) {
        Specification<CashRegisterEntity> spec = buildHistorySpec(status, sellerId, openedFrom, openedTo);
        return jpaRepository.findAll(spec, pageable).map(this::toDomain);
    }

    @Override
    public Page<CashRegister> findHistory(CashRegisterStatus status,
                                          UUID sellerId,
                                          LocalDateTime openedFrom,
                                          LocalDateTime openedTo,
                                          Pageable pageable) {
        Specification<CashRegisterEntity> spec = buildHistorySpec(status, sellerId, openedFrom, openedTo);
        return jpaRepository.findAll(spec, pageable).map(this::toDomain);
    }

    private static Specification<CashRegisterEntity> buildHistorySpec(CashRegisterStatus status,
                                                                      UUID sellerId,
                                                                      LocalDateTime openedFrom,
                                                                      LocalDateTime openedTo) {
        return (root, query, cb) -> {
            var predicates = cb.conjunction();

            if (status != null) {
                predicates.getExpressions().add(cb.equal(root.get("status"), status));
            }
            if (sellerId != null) {
                predicates.getExpressions().add(cb.equal(root.get("sellerId"), sellerId));
            }
            if (openedFrom != null) {
                predicates.getExpressions().add(cb.greaterThanOrEqualTo(root.get("openedAt"), openedFrom));
            }
            if (openedTo != null) {
                predicates.getExpressions().add(cb.lessThanOrEqualTo(root.get("openedAt"), openedTo));
            }

            return predicates;
        };
    }

    /**
     * Copies the aggregate onto a (possibly managed) entity instead of building a fresh one.
     * CashRegisterEntity carries @Version, and Spring Data treats a null version as "new", so
     * constructing a detached entity on every save made re-saving an existing register issue an
     * INSERT and violate cash_registers_pkey. Mirrors ProductRepositoryAdapter.save.
     */
    private CashRegisterEntity applyToEntity(CashRegister cr, CashRegisterEntity e) {
        e.setId(cr.getId());
        e.setSellerId(cr.getSellerId());
        e.setOpenedAt(cr.getOpenedAt());
        e.setClosedAt(cr.getClosedAt());
        e.setOpeningBalance(cr.getOpeningBalance());
        e.setClosingBalance(cr.getClosingBalance());
        if (cr.getStatus() == CashRegisterStatus.CLOSED) {
            e.setExpectedBalance(cr.getExpectedBalance());
            e.setDifference(cr.getDifference());
        }
        e.setStatus(cr.getStatus());
        e.setNotes(cr.getNotes());
        return e;
    }

    private CashRegister toDomain(CashRegisterEntity e) {
        List<CashMovement> movements = movementRepository.findByCashRegisterId(e.getId());
        return CashRegister.reconstruct(
                e.getId(), e.getSellerId(), e.getOpenedAt(), e.getClosedAt(),
                e.getOpeningBalance(), e.getClosingBalance(), e.getExpectedBalance(),
                e.getDifference(), e.getStatus(), e.getNotes(), movements);
    }
}
