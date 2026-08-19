package com.pilarestilo.billing.application.usecases;

import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Files the scan of a boleta that was registered without one.
 *
 * <p>This exists because of a corner the shop can be pushed into: the folio is unique for good,
 * voided rows included, so a boleta registered with the right folio and a failed upload could not
 * be corrected at all. Reissuing needs a new folio, and inventing one to work around a lost image
 * would put a number in the record that the SII never gave the shop.
 *
 * <p>Attaching is not correcting. The folio, the amounts and the buyer stay exactly as issued;
 * only the picture of the paper arrives late, which is the normal order of events when the boleta
 * is emitted by hand in the SII app and photographed afterwards.
 */
@Service
public class AttachDocumentFileUseCase {

    private static final Logger log = LoggerFactory.getLogger(AttachDocumentFileUseCase.class);

    private final SalesDocumentRepository salesDocumentRepository;

    public AttachDocumentFileUseCase(SalesDocumentRepository salesDocumentRepository) {
        this.salesDocumentRepository = salesDocumentRepository;
    }

    @Transactional
    public SalesDocument execute(UUID documentId, String storedFileName) {
        SalesDocument document = salesDocumentRepository.findById(documentId)
                .orElseThrow(() -> new DomainException("Sales document not found: " + documentId));
        document.attachFile(storedFileName);
        log.info("Attached a file to sales document {} (folio {})", documentId, document.getFolio());
        return salesDocumentRepository.save(document);
    }
}
