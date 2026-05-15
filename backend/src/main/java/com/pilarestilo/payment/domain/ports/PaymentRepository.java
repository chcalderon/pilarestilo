package com.pilarestilo.payment.domain.ports;

import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.domain.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByOrderId(UUID orderId);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    Page<Payment> findAll(Pageable pageable);

    List<Payment> findPendingBankTransfersOlderThan(Instant cutoff, int limit);
}
