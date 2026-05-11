package com.pilarestilo.customeraddress.application.mappers;

import com.pilarestilo.customeraddress.application.dto.CustomerAddressDto;
import com.pilarestilo.customeraddress.domain.model.CustomerAddress;

public final class CustomerAddressMapper {

    private CustomerAddressMapper() {
    }

    public static CustomerAddressDto toDto(CustomerAddress address) {
        return new CustomerAddressDto(
                address.getId(),
                address.getCustomerId(),
                address.getLabel(),
                address.getRecipientName(),
                address.getPhone(),
                address.getLine1(),
                address.getLine2(),
                address.getRegionId(),
                address.getCityId(),
                address.getCommuneId(),
                address.getComuna(),
                address.getCity(),
                address.getRegion(),
                address.getReference(),
                address.isDefault(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }
}
