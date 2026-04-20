package com.pilarestilo.customercredit.infrastructure.persistence.repositories;

import com.pilarestilo.customercredit.infrastructure.persistence.entities.CustomerCreditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerCreditJpaRepository extends JpaRepository<CustomerCreditEntity, UUID> {

    Optional<CustomerCreditEntity> findByCustomerId(UUID customerId);
}
