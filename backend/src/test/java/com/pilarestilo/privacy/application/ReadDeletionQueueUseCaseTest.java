package com.pilarestilo.privacy.application;

import com.pilarestilo.privacy.application.dto.DeletionQueueItemDto;
import com.pilarestilo.privacy.application.usecases.ReadDeletionQueueUseCase;
import com.pilarestilo.privacy.domain.enums.DeletionStatus;
import com.pilarestilo.privacy.domain.model.DataDeletionRequest;
import com.pilarestilo.privacy.domain.ports.DataDeletionRequestRepository;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The queue has to be answerable by a person at a desk.
 *
 * <p>Which means two things this pins: the row says who asked, not a UUID, and it says how long
 * they have been waiting — the Ley 21.719 gives the shop a fixed window to reply, and a clock that
 * keeps running after the answer measures nothing.
 */
@ExtendWith(MockitoExtension.class)
class ReadDeletionQueueUseCaseTest {

    @Mock DataDeletionRequestRepository deletionRepository;
    @Mock UserRepository userRepository;

    ReadDeletionQueueUseCase useCase;

    private User customer;

    @BeforeEach
    void setUp() {
        useCase = new ReadDeletionQueueUseCase(deletionRepository, userRepository);
        customer = User.create("ana@correo.cl", "Ana Perez", "+56911111111", UserRole.CUSTOMER, "hash");
        customer.setId(UUID.randomUUID());
    }

    private DataDeletionRequest requestedDaysAgo(int days) {
        return DataDeletionRequest.reconstruct(
                UUID.randomUUID(), customer.getId(), DeletionStatus.REQUESTED, "Ya no quiero cuenta",
                Instant.now().minus(days, ChronoUnit.DAYS), null, null, null,
                Instant.now().minus(days, ChronoUnit.DAYS));
    }

    @Test
    void the_row_names_the_person_who_asked() {
        when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        DeletionQueueItemDto item = useCase.describe(requestedDaysAgo(3));

        assertEquals("Ana Perez", item.customerName());
        assertEquals("ana@correo.cl", item.customerEmail());
    }

    @Test
    void the_wait_is_counted_from_the_day_they_asked() {
        when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        assertEquals(34, useCase.describe(requestedDaysAgo(34)).daysWaiting());
    }

    /** Once answered the clock stops, otherwise every closed request eventually reads as overdue. */
    @Test
    void the_wait_stops_on_the_day_it_was_answered() {
        Instant asked = Instant.now().minus(40, ChronoUnit.DAYS);
        DataDeletionRequest answered = DataDeletionRequest.reconstruct(
                UUID.randomUUID(), customer.getId(), DeletionStatus.ANONYMISED, null,
                asked, asked.plus(4, ChronoUnit.DAYS), UUID.randomUUID(), "Anonimizada", asked);
        when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        assertEquals(4, useCase.describe(answered).daysWaiting());
    }

    /**
     * After the request is carried out the name is the anonymised one, because it is read live.
     * That is the intent: the row showing "Cliente anonimizado" is the proof the work was done.
     */
    @Test
    void a_resolved_row_shows_the_anonymised_account() {
        customer.anonymise();
        when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        DeletionQueueItemDto item = useCase.describe(requestedDaysAgo(2));

        assertEquals("Cliente anonimizado", item.customerName());
    }

    @Test
    void a_missing_user_does_not_take_the_queue_down() {
        when(userRepository.findById(customer.getId())).thenReturn(Optional.empty());

        DeletionQueueItemDto item = useCase.describe(requestedDaysAgo(1));

        assertNull(item.customerName());
        assertNull(item.customerEmail());
        assertEquals(customer.getId(), item.userId());
    }

    @Test
    void the_open_queue_is_the_only_one_read_when_asked_for_open_only() {
        Pageable pageable = PageRequest.of(0, 20);
        when(deletionRepository.findOpen(pageable))
                .thenReturn(new PageImpl<>(List.of(requestedDaysAgo(5))));
        when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        Page<DeletionQueueItemDto> page = useCase.page(true, pageable);

        assertEquals(1, page.getTotalElements());
        assertEquals("REQUESTED", page.getContent().get(0).status());
        verify(deletionRepository, never()).findAll(pageable);
    }
}
