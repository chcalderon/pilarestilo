package com.pilarestilo.privacy.application;

import com.pilarestilo.customeraddress.domain.model.CustomerAddress;
import com.pilarestilo.customeraddress.domain.ports.CustomerAddressRepository;
import com.pilarestilo.privacy.application.usecases.ResolveDeletionRequestUseCase;
import com.pilarestilo.privacy.domain.enums.DeletionStatus;
import com.pilarestilo.privacy.domain.model.DataDeletionRequest;
import com.pilarestilo.privacy.domain.ports.DataDeletionRequestRepository;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Two obligations that look opposite and are not.
 *
 * <p>The Ley 21.719 gives the customer the right to be removed; the tax law makes the shop keep the
 * boleta for six years. The way out is anonymising: the account stops naming a person, the sale
 * stays answerable. What this pins is exactly which side of that line each thing falls on.
 */
@ExtendWith(MockitoExtension.class)
class ResolveDeletionRequestUseCaseTest {

    @Mock DataDeletionRequestRepository deletionRepository;
    @Mock UserRepository userRepository;
    @Mock CustomerAddressRepository addressRepository;

    ResolveDeletionRequestUseCase useCase;

    private final UUID actor = UUID.randomUUID();
    private User customer;
    private DataDeletionRequest request;

    @BeforeEach
    void setUp() {
        useCase = new ResolveDeletionRequestUseCase(deletionRepository, userRepository, addressRepository);

        customer = User.create("ana@correo.cl", "Ana Perez", "+56911111111", UserRole.CUSTOMER, "hash");
        customer.setId(UUID.randomUUID());
        request = DataDeletionRequest.open(customer.getId(), "Ya no quiero tener cuenta");

        lenient().when(deletionRepository.findById(request.getId())).thenReturn(Optional.of(request));
        lenient().when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        lenient().when(addressRepository.findByCustomerIdOrderByUpdatedAtDesc(customer.getId()))
                .thenReturn(List.of());
        lenient().when(deletionRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(userRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void anonymising_leaves_nothing_that_names_the_person() {
        useCase.anonymise(request.getId(), actor);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertNotEquals("ana@correo.cl", saved.getEmail());
        assertEquals("Cliente anonimizado", saved.getFullName());
        assertNull(saved.getPhone());
        assertNull(saved.getAvatarUrl());
        assertFalse(saved.isActive(), "there is no address left to sign in with");
    }

    /** The row survives: an order and a boleta point at it, and both outlive the account. */
    @Test
    void the_user_row_is_kept_rather_than_deleted() {
        useCase.anonymise(request.getId(), actor);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(customer.getId(), captor.getValue().getId());
    }

    /** A saved address has no retention of its own, and is the most directly identifying thing held. */
    @Test
    void every_delivery_address_goes() {
        CustomerAddress address = CustomerAddress.create(
                customer.getId(), "Casa", "Ana Perez", "+56911111111",
                "Santa Angela 92", null, 13, 45L, 273L, "Las Condes", "Santiago",
                "Region Metropolitana", null, true);
        when(addressRepository.findByCustomerIdOrderByUpdatedAtDesc(customer.getId()))
                .thenReturn(List.of(address));

        useCase.anonymise(request.getId(), actor);

        verify(addressRepository).deleteByIdAndCustomerId(address.getId(), customer.getId());
    }

    @Test
    void the_request_records_who_carried_it_out() {
        DataDeletionRequest resolved = useCase.anonymise(request.getId(), actor);

        assertEquals(DeletionStatus.ANONYMISED, resolved.getStatus());
        assertEquals(actor, resolved.getResolvedBy());
        assertNotNull(resolved.getResolvedAt());
    }

    @Test
    void refusing_without_a_reason_is_not_an_answer() {
        assertThrows(DomainException.class, () -> useCase.refuse(request.getId(), "  ", actor));
        verify(userRepository, never()).save(any());
    }

    @Test
    void a_refusal_carries_what_the_customer_is_owed() {
        DataDeletionRequest resolved = useCase.refuse(
                request.getId(), "Tiene un pedido en transito", actor);

        assertEquals(DeletionStatus.REFUSED, resolved.getStatus());
        assertEquals("Tiene un pedido en transito", resolved.getResolution());
        verify(userRepository, never()).save(any());
    }

    /** Resolving twice would let a refusal quietly become an anonymisation, or the reverse. */
    @Test
    void a_resolved_request_cannot_be_resolved_again() {
        useCase.anonymise(request.getId(), actor);

        assertThrows(DomainException.class, () -> useCase.refuse(request.getId(), "tarde", actor));
    }
}
