package com.pilarestilo.customeraddress.domain;

import com.pilarestilo.customeraddress.domain.model.CustomerAddress;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerAddressTest {

    @Test
    void create_rejects_missing_required_fields() {
        UUID customerId = UUID.randomUUID();
        assertThrows(DomainException.class, () -> CustomerAddress.create(
                customerId,
                "Casa",
                "",
                "+56912345678",
                "Linea 1",
                null,
                13,
                45L,
                273L,
                "Comuna",
                "Ciudad",
                "Region",
                null,
                true
        ));
    }

    @Test
    void create_rejects_invalid_phone() {
        UUID customerId = UUID.randomUUID();
        assertThrows(DomainException.class, () -> CustomerAddress.create(
                customerId,
                "Casa",
                "Pilar",
                "123",
                "Linea 1",
                null,
                13,
                45L,
                273L,
                "Comuna",
                "Ciudad",
                "Region",
                null,
                true
        ));
    }

    @Test
    void create_normalizes_and_trims_values() {
        UUID customerId = UUID.randomUUID();
        CustomerAddress address = CustomerAddress.create(
                customerId,
                "  Casa  ",
                " Pilar  ",
                " +56 9 1234 5678 ",
                " Av. Apoquindo 123 ",
                " Depto 4 ",
                13,
                45L,
                273L,
                " Las Condes ",
                " Santiago ",
                " Metropolitana ",
                " Conserjeria ",
                false
        );

        assertEquals("Casa", address.getLabel());
        assertEquals("Pilar", address.getRecipientName());
        assertEquals("+56912345678", address.getPhone());
        assertEquals("Av. Apoquindo 123", address.getLine1());
        assertEquals("Depto 4", address.getLine2());
        assertEquals(13, address.getRegionId());
        assertEquals(45L, address.getCityId());
        assertEquals(273L, address.getCommuneId());
        assertEquals("Las Condes", address.getComuna());
        assertEquals("Santiago", address.getCity());
        assertEquals("Metropolitana", address.getRegion());
        assertEquals("Conserjeria", address.getReference());
    }
}
