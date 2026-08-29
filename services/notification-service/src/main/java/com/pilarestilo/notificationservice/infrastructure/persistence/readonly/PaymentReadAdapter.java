package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import com.pilarestilo.notificationservice.domain.ports.PaymentReadPort;
import com.pilarestilo.notificationservice.domain.view.PaymentView;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentReadAdapter implements PaymentReadPort {

    private final PaymentRoRepository repository;

    public PaymentReadAdapter(PaymentRoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PaymentView> findById(UUID paymentId) {
        return repository.findById(paymentId).map(e -> new PaymentView(
                e.getId(), e.getOrderId(), e.getMethod(), e.getStatus(),
                e.getRejectionReason(), e.getProofReference(), e.getCreatedAt(),
                e.getTransferAccountHolderName(), e.getTransferBankName(), e.getTransferAccountType(),
                e.getTransferAccountNumber(), e.getTransferAccountEmail()));
    }
}
