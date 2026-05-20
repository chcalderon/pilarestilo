package com.pilarestilo.product.infrastructure.persistence.entities;

import com.pilarestilo.product.domain.enums.ShippingOriginZone;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ShippingOriginZoneAttributeConverter implements AttributeConverter<ShippingOriginZone, String> {

    @Override
    public String convertToDatabaseColumn(ShippingOriginZone attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public ShippingOriginZone convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        if ("NATIONAL".equals(dbData)) {
            return ShippingOriginZone.NACIONAL;
        }
        return ShippingOriginZone.valueOf(dbData);
    }
}
