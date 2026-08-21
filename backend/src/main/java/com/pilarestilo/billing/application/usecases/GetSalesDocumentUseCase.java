package com.pilarestilo.billing.application.usecases;

import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * One document by id, for the download endpoint that has to name the file after the folio it
 * carries. Returns the domain model rather than a DTO because the caller needs the type and the
 * folio to build a Content-Disposition, not something to serialise.
 */
@Service
public class GetSalesDocumentUseCase {

    private final SalesDocumentRepository salesDocumentRepository;

    public GetSalesDocumentUseCase(SalesDocumentRepository salesDocumentRepository) {
        this.salesDocumentRepository = salesDocumentRepository;
    }

    @Transactional(readOnly = true)
    public SalesDocument execute(UUID documentId) {
        return salesDocumentRepository.findById(documentId)
                .orElseThrow(() -> new DomainException("Sales document not found: " + documentId));
    }
}
