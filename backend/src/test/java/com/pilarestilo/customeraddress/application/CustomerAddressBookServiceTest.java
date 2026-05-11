package com.pilarestilo.customeraddress.application;

import com.pilarestilo.customeraddress.domain.model.CustomerAddress;
import com.pilarestilo.customeraddress.domain.ports.CustomerAddressRepository;
import com.pilarestilo.location.application.LocationCatalogService;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAddressBookServiceTest {

    @Mock
    CustomerAddressRepository repository;

    @Mock
    LocationCatalogService locationCatalogService;

    @InjectMocks
    CustomerAddressBookService service;

    private LocationCatalogService.ResolvedLocation lasCondes() {
        return new LocationCatalogService.ResolvedLocation(
                13,
                "Region Metropolitana de Santiago",
                45L,
                "Santiago",
                273L,
                "Las Condes"
        );
    }

    @Test
    void create_first_address_for_customer_forces_default() {
        UUID customerId = UUID.randomUUID();
        when(repository.countByCustomerId(customerId)).thenReturn(0L);
        when(locationCatalogService.resolveSelection(13, 45L, 273L)).thenReturn(lasCondes());
        when(repository.save(any(CustomerAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerAddress saved = service.create(
                customerId,
                "Casa",
                "  Pilar  ",
                " +56 9 1234 5678 ",
                " Av. Apoquindo 123 ",
                null,
                13,
                45L,
                273L,
                " Las Condes ",
                " Santiago ",
                " Metropolitana ",
                null,
                false
        );

        assertNotNull(saved.getId());
        assertEquals("Pilar", saved.getRecipientName());
        assertEquals("+56912345678", saved.getPhone());
        assertEquals("Av. Apoquindo 123", saved.getLine1());
        assertEquals("Las Condes", saved.getComuna());
        assertEquals("Santiago", saved.getCity());
        assertEquals("Region Metropolitana de Santiago", saved.getRegion());
        assertEquals("Casa", saved.getLabel());
        assertEquals(customerId, saved.getCustomerId());
        assertEquals(true, saved.isDefault());
        verify(repository, never()).clearDefaultForCustomer(customerId);
    }

    @Test
    void create_rejects_when_customer_has_ten_addresses() {
        UUID customerId = UUID.randomUUID();
        when(repository.countByCustomerId(customerId)).thenReturn(10L);

        assertThrows(DomainException.class, () -> service.create(
                customerId,
                "Oficina",
                "Pilar",
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
                false
        ));
    }

    @Test
    void create_marking_new_default_clears_previous_default() {
        UUID customerId = UUID.randomUUID();
        when(repository.countByCustomerId(customerId)).thenReturn(2L);
        when(locationCatalogService.resolveSelection(13, 45L, 273L)).thenReturn(lasCondes());
        when(repository.save(any(CustomerAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerAddress saved = service.create(
                customerId,
                "Sucursal",
                "Pilar",
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
        );

        assertEquals(true, saved.isDefault());
        verify(repository).clearDefaultForCustomer(customerId);
    }

    @Test
    void set_default_reassigns_to_target_address() {
        UUID customerId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        CustomerAddress address = CustomerAddress.reconstruct(
                addressId,
                customerId,
                "Casa",
                "Pilar",
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
                false,
                Instant.now(),
                Instant.now()
        );
        when(repository.findByIdAndCustomerId(addressId, customerId)).thenReturn(Optional.of(address));
        when(repository.save(any(CustomerAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerAddress saved = service.setDefault(customerId, addressId);

        verify(repository).clearDefaultForCustomer(customerId);
        assertEquals(true, saved.isDefault());
    }

    @Test
    void update_keeps_default_and_normalizes_fields() {
        UUID customerId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        CustomerAddress address = CustomerAddress.reconstruct(
                addressId,
                customerId,
                "Casa",
                "Pilar",
                "+56911111111",
                "Linea 1",
                null,
                13,
                45L,
                273L,
                "Comuna",
                "Ciudad",
                "Region",
                null,
                true,
                Instant.now(),
                Instant.now()
        );

        when(repository.findByIdAndCustomerId(addressId, customerId)).thenReturn(Optional.of(address));
        when(locationCatalogService.resolveSelection(13, 45L, 281L))
                .thenReturn(new LocationCatalogService.ResolvedLocation(
                        13,
                        "Region Metropolitana de Santiago",
                        45L,
                        "Santiago",
                        281L,
                        "Providencia"
                ));
        when(repository.save(any(CustomerAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerAddress saved = service.update(
                customerId,
                addressId,
                " Oficina ",
                "  Pilar Estilo ",
                " +56 9 8765 4321 ",
                " Linea 100 ",
                " Torre A ",
                13,
                45L,
                281L,
                " Providencia ",
                " Santiago ",
                " Metropolitana ",
                " Frente al parque "
        );

        assertEquals("Oficina", saved.getLabel());
        assertEquals("Pilar Estilo", saved.getRecipientName());
        assertEquals("+56987654321", saved.getPhone());
        assertEquals("Linea 100", saved.getLine1());
        assertEquals("Torre A", saved.getLine2());
        assertEquals("Providencia", saved.getComuna());
        assertEquals("Santiago", saved.getCity());
        assertEquals("Region Metropolitana de Santiago", saved.getRegion());
        assertEquals("Frente al parque", saved.getReference());
        assertEquals(true, saved.isDefault());

        ArgumentCaptor<CustomerAddress> captor = ArgumentCaptor.forClass(CustomerAddress.class);
        verify(repository).save(captor.capture());
        assertFalse(captor.getValue().getUpdatedAt().isBefore(captor.getValue().getCreatedAt()));
    }

    @Test
    void list_orders_by_updated_at_desc() {
        UUID customerId = UUID.randomUUID();
        when(repository.findByCustomerIdOrderByUpdatedAtDesc(customerId)).thenReturn(List.of());
        service.list(customerId);
        verify(repository).findByCustomerIdOrderByUpdatedAtDesc(customerId);
    }
}
