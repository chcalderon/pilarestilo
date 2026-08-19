package com.pilarestilo.returns.infrastructure;

import com.pilarestilo.returns.domain.enums.ItemDisposition;
import com.pilarestilo.returns.domain.enums.RefundMethod;
import com.pilarestilo.returns.domain.enums.ReturnKind;
import com.pilarestilo.returns.domain.enums.ReturnStatus;
import com.pilarestilo.returns.domain.model.RefundAccount;
import com.pilarestilo.returns.domain.model.ReturnRequest;
import com.pilarestilo.returns.infrastructure.persistence.entities.ReturnRequestEntity;
import com.pilarestilo.returns.infrastructure.persistence.repositories.ReturnRequestJpaRepository;
import com.pilarestilo.returns.infrastructure.persistence.repositories.ReturnRequestRepositoryAdapter;
import com.pilarestilo.shared.application.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The mapping is where a return quietly loses half of itself.
 *
 * <p>A return carries two independent tracks — the money and the garment — plus bank details that
 * are encrypted and then erased. Every one of those is a column that has to survive the round trip;
 * a field dropped here reads as a return that was never refunded, or one whose account number was
 * never wiped.
 */
@ExtendWith(MockitoExtension.class)
class ReturnRequestRepositoryAdapterTest {

    @Mock ReturnRequestJpaRepository jpaRepository;

    ReturnRequestRepositoryAdapter adapter;

    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new ReturnRequestRepositoryAdapter(jpaRepository);
    }

    private ReturnRequest refundedReturn() {
        ReturnRequest request = ReturnRequest.open(
                orderId, ReturnKind.RETRACTO, "No me quedó como esperaba", customerId);
        request.attachRefundAccount(RefundAccount.of(
                "Ana Perez", "11.111.111-1", "Banco Estado", "Cuenta Vista", "cifrado", "6789"));
        request.approve();
        request.receive();
        request.resolveDisposition(ItemDisposition.RESTOCKED, null);
        request.registerRefund(Money.of(new BigDecimal("45990")), RefundMethod.TRANSFERENCIA,
                "OP-99881", "comprobante.pdf");
        return request;
    }

    @Test
    void a_return_survives_the_round_trip_whole() {
        ReturnRequest original = refundedReturn();
        when(jpaRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        ReturnRequest saved = adapter.save(original);

        assertEquals(original.getId(), saved.getId());
        assertEquals(orderId, saved.getOrderId());
        assertEquals(ReturnKind.RETRACTO, saved.getKind());
        assertEquals(ReturnStatus.REFUNDED, saved.getStatus());
        assertEquals("No me quedó como esperaba", saved.getReason());
        assertEquals(customerId, saved.getRequestedBy());
        assertNotNull(saved.getDeadlineAt());
        assertEquals(ItemDisposition.RESTOCKED, saved.getItemDisposition());
        assertEquals(new BigDecimal("45990"), saved.getRefundAmount().amount());
        assertEquals(RefundMethod.TRANSFERENCIA, saved.getRefundMethod());
        assertEquals("OP-99881", saved.getRefundReference());
        assertEquals("comprobante.pdf", saved.getRefundFileUrl());
        assertNotNull(saved.getRefundedAt());
    }

    /**
     * The account number is erased when the refund settles, and what identifies the payment
     * afterwards is the last four digits. Both facts have to reach the row.
     */
    @Test
    void the_erased_account_is_what_reaches_the_row() {
        when(jpaRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        adapter.save(refundedReturn());

        ArgumentCaptor<ReturnRequestEntity> captor = ArgumentCaptor.forClass(ReturnRequestEntity.class);
        verify(jpaRepository).save(captor.capture());
        ReturnRequestEntity entity = captor.getValue();
        assertNull(entity.getRefundAccountEncrypted(), "the number stops existing once the money moved");
        assertEquals("6789", entity.getRefundAccountLast4());
        assertEquals("Banco Estado", entity.getRefundBankName());
    }

    @Test
    void an_open_return_keeps_its_nulls_rather_than_inventing_values() {
        ReturnRequest open = ReturnRequest.open(orderId, ReturnKind.DEVOLUCION, "No le quedó", null);
        when(jpaRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        ReturnRequest saved = adapter.save(open);

        assertEquals(ReturnStatus.REQUESTED, saved.getStatus());
        assertNull(saved.getRequestedBy(), "the shop opened it, not a customer");
        assertNull(saved.getRefundAmount());
        assertNull(saved.getRefundMethod());
        assertNull(saved.getItemDisposition());
        assertNull(saved.getResolvedAt());
    }

    @Test
    void reads_go_through_the_same_mapping() {
        ReturnRequest request = refundedReturn();
        when(jpaRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        ReturnRequestEntity entity;
        adapter.save(request);
        ArgumentCaptor<ReturnRequestEntity> captor = ArgumentCaptor.forClass(ReturnRequestEntity.class);
        verify(jpaRepository).save(captor.capture());
        entity = captor.getValue();

        when(jpaRepository.findById(request.getId())).thenReturn(Optional.of(entity));
        when(jpaRepository.findOpenByOrderId(orderId)).thenReturn(Optional.of(entity));
        when(jpaRepository.findByOrderIdOrderByRequestedAtDesc(orderId)).thenReturn(List.of(entity));
        when(jpaRepository.findByRequestedByOrderByRequestedAtDesc(customerId)).thenReturn(List.of(entity));
        when(jpaRepository.findOpenByDeadline(any())).thenReturn(new PageImpl<>(List.of(entity)));
        when(jpaRepository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(entity)));

        assertTrue(adapter.findById(request.getId()).isPresent());
        assertTrue(adapter.findOpenByOrderId(orderId).isPresent());
        assertEquals(1, adapter.findAllByOrderId(orderId).size());
        assertEquals(1, adapter.findByRequestedBy(customerId).size());
        assertEquals(1, adapter.findOpenByDeadline(PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, adapter.findAll(PageRequest.of(0, 10)).getTotalElements());
    }
}
