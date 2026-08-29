package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import com.pilarestilo.notificationservice.domain.ports.SalesDocumentReadPort;
import com.pilarestilo.notificationservice.domain.view.Money;
import com.pilarestilo.notificationservice.domain.view.SalesDocumentView;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class SalesDocumentReadAdapter implements SalesDocumentReadPort {

    private final SalesDocumentRoRepository repository;

    public SalesDocumentReadAdapter(SalesDocumentRoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<SalesDocumentView> findById(UUID documentId) {
        return repository.findById(documentId).map(e -> new SalesDocumentView(
                e.getId(), e.getDocumentType(), e.getFolio(),
                Money.of(e.getNetAmount(), e.getCurrency()),
                Money.of(e.getTaxAmount(), e.getCurrency()),
                e.getTaxRate(),
                Money.of(e.getTotalAmount(), e.getCurrency())));
    }
}
