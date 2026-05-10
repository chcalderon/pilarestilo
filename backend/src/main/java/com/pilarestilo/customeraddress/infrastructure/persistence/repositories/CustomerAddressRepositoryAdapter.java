package com.pilarestilo.customeraddress.infrastructure.persistence.repositories;

import com.pilarestilo.customeraddress.domain.model.CustomerAddress;
import com.pilarestilo.customeraddress.domain.ports.CustomerAddressRepository;
import com.pilarestilo.customeraddress.infrastructure.persistence.entities.CustomerAddressEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CustomerAddressRepositoryAdapter implements CustomerAddressRepository {

    private final CustomerAddressJpaRepository jpaRepository;

    public CustomerAddressRepositoryAdapter(CustomerAddressJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CustomerAddress save(CustomerAddress address) {
        CustomerAddressEntity entity = toEntity(address);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<CustomerAddress> findByIdAndCustomerId(UUID addressId, UUID customerId) {
        return jpaRepository.findByIdAndCustomerId(addressId, customerId).map(this::toDomain);
    }

    @Override
    public List<CustomerAddress> findByCustomerIdOrderByUpdatedAtDesc(UUID customerId) {
        return jpaRepository.findByCustomerIdOrderByUpdatedAtDesc(customerId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByCustomerId(UUID customerId) {
        return jpaRepository.countByCustomerId(customerId);
    }

    @Override
    public void clearDefaultForCustomer(UUID customerId) {
        jpaRepository.clearDefaultForCustomer(customerId);
    }

    @Override
    public void deleteByIdAndCustomerId(UUID addressId, UUID customerId) {
        jpaRepository.deleteByIdAndCustomerId(addressId, customerId);
    }

    private CustomerAddressEntity toEntity(CustomerAddress address) {
        CustomerAddressEntity entity = new CustomerAddressEntity();
        entity.setId(address.getId());
        entity.setCustomerId(address.getCustomerId());
        entity.setLabel(address.getLabel());
        entity.setRecipientName(address.getRecipientName());
        entity.setPhone(address.getPhone());
        entity.setLine1(address.getLine1());
        entity.setLine2(address.getLine2());
        entity.setComuna(address.getComuna());
        entity.setCity(address.getCity());
        entity.setRegion(address.getRegion());
        entity.setReference(address.getReference());
        entity.setDefault(address.isDefault());
        entity.setCreatedAt(address.getCreatedAt());
        entity.setUpdatedAt(address.getUpdatedAt());
        return entity;
    }

    private CustomerAddress toDomain(CustomerAddressEntity entity) {
        return CustomerAddress.reconstruct(
                entity.getId(),
                entity.getCustomerId(),
                entity.getLabel(),
                entity.getRecipientName(),
                entity.getPhone(),
                entity.getLine1(),
                entity.getLine2(),
                entity.getComuna(),
                entity.getCity(),
                entity.getRegion(),
                entity.getReference(),
                entity.isDefault(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

