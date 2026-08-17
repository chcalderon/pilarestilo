package com.pilarestilo.billing.application.usecases;

import com.pilarestilo.billing.application.dto.SalesDocumentDto;
import com.pilarestilo.billing.application.mappers.SalesDocumentMapper;
import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Marks a document void, keeping the row.
 *
 * <p>Voiding does not touch the order. Closing a sale as cancelled is a separate decision the caller
 * makes afterwards, and keeping them apart is what lets a wrong folio be corrected without
 * cancelling a sale that actually happened.
 */
@Service
public class VoidSalesDocumentUseCase {

    private final SalesDocumentRepository salesDocumentRepository;

    public VoidSalesDocumentUseCase(SalesDocumentRepository salesDocumentRepository) {
        this.salesDocumentRepository = salesDocumentRepository;
    }

    @Transactional
    public SalesDocumentDto execute(UUID documentId, String reason, UUID voidedBy) {
        SalesDocument document = salesDocumentRepository.findById(documentId)
                .orElseThrow(() -> new DomainException("Sales document not found: " + documentId));
        document.voidDocument(reason, voidedBy);
        return SalesDocumentMapper.toDto(salesDocumentRepository.save(document));
    }
}
