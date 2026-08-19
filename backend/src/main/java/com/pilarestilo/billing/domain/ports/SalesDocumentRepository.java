package com.pilarestilo.billing.domain.ports;

import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.model.SalesDocument;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SalesDocumentRepository {

    SalesDocument save(SalesDocument document);

    Optional<SalesDocument> findById(UUID id);

    /**
     * The one <em>sale</em> document that counts for an order: everything else behind it has been
     * voided. Credit notes are excluded on purpose — one lives alongside the boleta it undoes.
     */
    Optional<SalesDocument> findLiveByOrderId(UUID orderId);

    /** The orders among these that have a live sale document. Same rule as above, asked in bulk. */
    Set<UUID> findOrderIdsWithLiveDocument(Collection<UUID> orderIds);

    /** Credit notes still standing against a document, to keep them from over-crediting it. */
    List<SalesDocument> findLiveCreditNotesFor(UUID documentId);

    /** Every attempt for an order, newest first, so a correction can be read next to what it corrected. */
    List<SalesDocument> findAllByOrderId(UUID orderId);

    boolean existsByTypeAndFolio(SalesDocumentType type, String folio);

    /**
     * Every stored file name a document points at, voided documents included: a voided boleta keeps
     * its file, because that file is the record of what was voided. Feeds the orphan sweep.
     */
    Set<String> findAllStoredFileNames();
}
