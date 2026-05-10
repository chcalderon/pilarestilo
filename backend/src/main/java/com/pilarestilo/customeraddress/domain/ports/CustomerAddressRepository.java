package com.pilarestilo.customeraddress.domain.ports;

import com.pilarestilo.customeraddress.domain.model.CustomerAddress;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository {

    CustomerAddress save(CustomerAddress address);

    Optional<CustomerAddress> findByIdAndCustomerId(UUID addressId, UUID customerId);

    List<CustomerAddress> findByCustomerIdOrderByUpdatedAtDesc(UUID customerId);

    long countByCustomerId(UUID customerId);

    void clearDefaultForCustomer(UUID customerId);

    void deleteByIdAndCustomerId(UUID addressId, UUID customerId);
}

