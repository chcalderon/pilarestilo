package com.pilarestilo.product.infrastructure.persistence.entities;

import com.pilarestilo.product.domain.enums.ShippingOriginZone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShippingOriginZoneAttributeConverterTest {

    private final ShippingOriginZoneAttributeConverter converter = new ShippingOriginZoneAttributeConverter();

    @Test
    void mapsLegacyNationalAliasToNacional() {
        assertEquals(ShippingOriginZone.NACIONAL, converter.convertToEntityAttribute("NATIONAL"));
    }

    @Test
    void preservesCanonicalEnumValues() {
        assertEquals(ShippingOriginZone.LOCAL, converter.convertToEntityAttribute("LOCAL"));
        assertEquals(ShippingOriginZone.REGIONAL, converter.convertToEntityAttribute("REGIONAL"));
        assertEquals(ShippingOriginZone.NACIONAL, converter.convertToEntityAttribute("NACIONAL"));
    }

    @Test
    void writesCanonicalEnumValueBackToDatabase() {
        assertEquals("NACIONAL", converter.convertToDatabaseColumn(ShippingOriginZone.NACIONAL));
    }

    @Test
    void keepsNullValuesAsNull() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}
