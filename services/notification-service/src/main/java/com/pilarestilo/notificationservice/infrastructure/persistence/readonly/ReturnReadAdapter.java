package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import com.pilarestilo.notificationservice.domain.ports.ReturnReadPort;
import com.pilarestilo.notificationservice.domain.view.Money;
import com.pilarestilo.notificationservice.domain.view.ReturnView;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ReturnReadAdapter implements ReturnReadPort {

    private final ReturnRequestRoRepository repository;

    public ReturnReadAdapter(ReturnRequestRoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ReturnView> findById(UUID returnId) {
        return repository.findById(returnId).map(e -> new ReturnView(
                e.getId(), e.getOrderId(), e.getKind(), e.getReason(), e.getDeadlineAt(),
                e.getRefundAmount() == null ? null : Money.of(e.getRefundAmount(), e.getRefundCurrency()),
                e.getRefundMethod(), e.getRefundReference(), e.getRefundedAt()));
    }
}
