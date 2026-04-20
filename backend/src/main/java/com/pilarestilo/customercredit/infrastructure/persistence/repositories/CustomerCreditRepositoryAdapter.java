package com.pilarestilo.customercredit.infrastructure.persistence.repositories;

import com.pilarestilo.customercredit.domain.model.CustomerCredit;
import com.pilarestilo.customercredit.domain.ports.CustomerCreditRepository;
import com.pilarestilo.customercredit.infrastructure.persistence.entities.CustomerCreditEntity;
import com.pilarestilo.shared.application.Money;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CustomerCreditRepositoryAdapter implements CustomerCreditRepository {

    private final CustomerCreditJpaRepository jpaRepository;

    public CustomerCreditRepositoryAdapter(CustomerCreditJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CustomerCredit save(CustomerCredit cc) {
        CustomerCreditEntity entity = toEntity(cc);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<CustomerCredit> findByCustomerId(UUID customerId) {
        return jpaRepository.findByCustomerId(customerId).map(this::toDomain);
    }

    private CustomerCreditEntity toEntity(CustomerCredit cc) {
        CustomerCreditEntity e = new CustomerCreditEntity();
        e.setId(cc.getId());
        e.setCustomerId(cc.getCustomerId());
        e.setBalanceAmount(cc.getBalance().amount());
        e.setBalanceCurrency(cc.getBalance().currency());
        return e;
    }

    private CustomerCredit toDomain(CustomerCreditEntity e) {
        return CustomerCredit.reconstruct(
                e.getId(), e.getCustomerId(),
                new Money(e.getBalanceAmount(), e.getBalanceCurrency())
        );
    }
}
