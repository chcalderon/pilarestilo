package com.pilarestilo.billing.application.usecases;

import com.pilarestilo.billing.application.dto.SalesDocumentDto;
import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Corrects a document: voids the current one and issues its replacement in one transaction.
 *
 * <p>Doing it in two calls would leave a window where the order has no live document, and the
 * dispatch gate reads exactly that. The chain through {@code replacesDocumentId} is what makes a
 * correction readable a year later as a correction rather than as two unrelated documents.
 */
@Service
public class ReissueSalesDocumentUseCase {

    private final SalesDocumentRepository salesDocumentRepository;
    private final IssueSalesDocumentUseCase issueSalesDocumentUseCase;

    public ReissueSalesDocumentUseCase(SalesDocumentRepository salesDocumentRepository,
                                       IssueSalesDocumentUseCase issueSalesDocumentUseCase) {
        this.salesDocumentRepository = salesDocumentRepository;
        this.issueSalesDocumentUseCase = issueSalesDocumentUseCase;
    }

    @Transactional
    public SalesDocumentDto execute(UUID documentId,
                                    String voidReason,
                                    SalesDocumentType type,
                                    String folio,
                                    String receiverRut,
                                    String receiverBusinessName,
                                    String receiverBusinessActivity,
                                    String fileUrl,
                                    UUID actorId) {
        SalesDocument previous = salesDocumentRepository.findById(documentId)
                .orElseThrow(() -> new DomainException("Sales document not found: " + documentId));

        previous.voidDocument(voidReason, actorId);
        salesDocumentRepository.save(previous);

        return issueSalesDocumentUseCase.issue(
                previous.getOrderId(), type, folio, receiverRut, receiverBusinessName, receiverBusinessActivity,
                fileUrl, actorId, previous.getId());
    }
}
