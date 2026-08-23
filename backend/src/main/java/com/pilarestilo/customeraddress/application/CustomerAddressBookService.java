package com.pilarestilo.customeraddress.application;

import com.pilarestilo.customeraddress.domain.model.CustomerAddress;
import com.pilarestilo.customeraddress.domain.ports.CustomerAddressRepository;
import com.pilarestilo.location.application.LocationCatalogService;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerAddressBookService {

    private static final long MAX_ADDRESSES_PER_CUSTOMER = 10L;

    private final CustomerAddressRepository repository;
    private final LocationCatalogService locationCatalogService;

    public CustomerAddressBookService(
            CustomerAddressRepository repository,
            LocationCatalogService locationCatalogService
    ) {
        this.repository = repository;
        this.locationCatalogService = locationCatalogService;
    }

    @Transactional(readOnly = true)
    public List<CustomerAddress> list(UUID customerId) {
        return repository.findByCustomerIdOrderByUpdatedAtDesc(customerId);
    }

    // One parameter per field the address form actually submits.
    @SuppressWarnings("java:S107")
    @Transactional
    public CustomerAddress create(
            UUID customerId,
            String label,
            String recipientName,
            String phone,
            String line1,
            String line2,
            Integer regionId,
            Long cityId,
            Long communeId,
            String comuna,
            String city,
            String region,
            String reference,
            boolean isDefaultRequested
    ) {
        long existingCount = repository.countByCustomerId(customerId);
        if (existingCount >= MAX_ADDRESSES_PER_CUSTOMER) {
            throw new DomainException("Address limit reached. Maximum is 10 addresses per customer.");
        }

        boolean isFirstAddress = existingCount == 0;
        boolean willBeDefault = isFirstAddress || isDefaultRequested;
        if (willBeDefault && !isFirstAddress) {
            repository.clearDefaultForCustomer(customerId);
        }

        LocationCatalogService.ResolvedLocation resolvedLocation =
                locationCatalogService.resolveSelection(regionId, cityId, communeId);

        CustomerAddress address = CustomerAddress.create(
                customerId,
                label,
                recipientName,
                phone,
                line1,
                line2,
                resolvedLocation.regionId(),
                resolvedLocation.cityId(),
                resolvedLocation.communeId(),
                resolvedLocation.communeName(),
                resolvedLocation.cityName(),
                resolvedLocation.regionName(),
                reference,
                willBeDefault
        );
        return repository.save(address);
    }

    @SuppressWarnings("java:S107")
    @Transactional
    public CustomerAddress update(
            UUID customerId,
            UUID addressId,
            String label,
            String recipientName,
            String phone,
            String line1,
            String line2,
            Integer regionId,
            Long cityId,
            Long communeId,
            String comuna,
            String city,
            String region,
            String reference
    ) {
        CustomerAddress address = repository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Address not found: " + addressId));
        LocationCatalogService.ResolvedLocation resolvedLocation =
                locationCatalogService.resolveSelection(regionId, cityId, communeId);
        address.update(
                label,
                recipientName,
                phone,
                line1,
                line2,
                resolvedLocation.regionId(),
                resolvedLocation.cityId(),
                resolvedLocation.communeId(),
                resolvedLocation.communeName(),
                resolvedLocation.cityName(),
                resolvedLocation.regionName(),
                reference
        );
        return repository.save(address);
    }

    @Transactional
    public void delete(UUID customerId, UUID addressId) {
        repository.deleteByIdAndCustomerId(addressId, customerId);
    }

    @Transactional
    public CustomerAddress setDefault(UUID customerId, UUID addressId) {
        CustomerAddress address = repository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Address not found: " + addressId));
        repository.clearDefaultForCustomer(customerId);
        address.markAsDefault();
        return repository.save(address);
    }

    @Transactional(readOnly = true)
    public CustomerAddress resolveOwnedAddress(UUID customerId, UUID addressId) {
        if (addressId == null) {
            throw new DomainException("shippingAddressId is required");
        }
        return repository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new DomainException("Shipping address not found for customer"));
    }
}
