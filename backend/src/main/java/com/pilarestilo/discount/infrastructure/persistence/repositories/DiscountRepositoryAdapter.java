package com.pilarestilo.discount.infrastructure.persistence.repositories;

import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import com.pilarestilo.discount.infrastructure.persistence.entities.DiscountEntity;
import com.pilarestilo.shared.application.Money;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class DiscountRepositoryAdapter implements DiscountRepository {

    private final DiscountJpaRepository jpaRepository;

    public DiscountRepositoryAdapter(DiscountJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Discount save(Discount discount) {
        DiscountEntity entity = toEntity(discount);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Discount> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Discount> findByCode(String code) {
        return jpaRepository.findByCode(code).map(this::toDomain);
    }

    @Override
    public Page<Discount> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    private DiscountEntity toEntity(Discount discount) {
        DiscountEntity entity = new DiscountEntity();
        entity.setId(discount.getId());
        entity.setCode(discount.getCode());
        entity.setType(discount.getType());
        entity.setValue(discount.getValue());
        entity.setMinOrderAmount(discount.getMinOrderAmount().amount());
        entity.setMinOrderCurrency(discount.getMinOrderAmount().currency());
        entity.setValidFrom(discount.getValidFrom());
        entity.setValidUntil(discount.getValidUntil());
        entity.setMaxUses(discount.getMaxUses());
        entity.setTimesUsed(discount.getTimesUsed());
        entity.setActive(discount.isActive());
        return entity;
    }

    private Discount toDomain(DiscountEntity entity) {
        Discount discount = Discount.create(
                entity.getCode(),
                entity.getType(),
                entity.getValue(),
                new Money(entity.getMinOrderAmount(), entity.getMinOrderCurrency()),
                entity.getValidFrom(),
                entity.getValidUntil(),
                entity.getMaxUses()
        );
        discount.setId(entity.getId());
        discount.setTimesUsed(entity.getTimesUsed());
        discount.setActive(entity.isActive());
        return discount;
    }
}
