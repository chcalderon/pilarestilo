package com.pilarestilo.orderservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddressEntity, UUID> {

    Optional<CustomerAddressEntity> findByIdAndCustomerId(UUID id, UUID customerId);
}

