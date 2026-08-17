package com.pilarestilo.billing.domain.ports;

import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.model.SalesDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesDocumentRepository {

    SalesDocument save(SalesDocument document);

    Optional<SalesDocument> findById(UUID id);

    /** The one document that counts for an order: everything else behind it has been voided. */
    Optional<SalesDocument> findLiveByOrderId(UUID orderId);

    /** Every attempt for an order, newest first, so a correction can be read next to what it corrected. */
    List<SalesDocument> findAllByOrderId(UUID orderId);

    boolean existsByTypeAndFolio(SalesDocumentType type, String folio);
}
