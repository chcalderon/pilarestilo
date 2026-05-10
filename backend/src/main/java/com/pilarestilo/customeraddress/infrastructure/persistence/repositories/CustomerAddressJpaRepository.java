package com.pilarestilo.customeraddress.infrastructure.persistence.repositories;

import com.pilarestilo.customeraddress.infrastructure.persistence.entities.CustomerAddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressJpaRepository extends JpaRepository<CustomerAddressEntity, UUID> {

    Optional<CustomerAddressEntity> findByIdAndCustomerId(UUID id, UUID customerId);

    List<CustomerAddressEntity> findByCustomerIdOrderByUpdatedAtDesc(UUID customerId);

    long countByCustomerId(UUID customerId);

    void deleteByIdAndCustomerId(UUID id, UUID customerId);

    @Modifying
    @Query("update CustomerAddressEntity a set a.isDefault = false where a.customerId = :customerId and a.isDefault = true")
    int clearDefaultForCustomer(@Param("customerId") UUID customerId);
}

