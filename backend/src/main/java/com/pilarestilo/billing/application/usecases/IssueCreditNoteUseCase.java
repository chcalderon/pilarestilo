package com.pilarestilo.billing.application.usecases;

import com.pilarestilo.billing.application.dto.SalesDocumentDto;
import com.pilarestilo.billing.application.mappers.SalesDocumentMapper;
import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.events.SalesDocumentIssued;
import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.returns.domain.model.ReturnRequest;
import com.pilarestilo.returns.domain.ports.ReturnRequestRepository;
import com.pilarestilo.shared.application.AfterCommitPublisher;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.application.TaxBreakdown;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Registers the nota de credito (DTE 61) that undoes a sale already declared to the SII.
 *
 * <p>Like the boleta, the document itself is emitted by hand — in eBoleta or on the SII site — and
 * only recorded here. Nothing in this class talks to the SII.
 *
 * <p>Voiding is not an alternative. A boleta that never left the shop can be voided and reissued,
 * and {@link ReissueSalesDocumentUseCase} does that. Once the sale is declared, the only honest way
 * to undo it is a second document that references the first: the boleta stays valid and the credit
 * note counterweighs it, in whole or in part.
 */
@Service
public class IssueCreditNoteUseCase {

    private final SalesDocumentRepository salesDocumentRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final AfterCommitPublisher eventPublisher;

    public IssueCreditNoteUseCase(SalesDocumentRepository salesDocumentRepository,
                                  ReturnRequestRepository returnRequestRepository,
                                  AfterCommitPublisher eventPublisher) {
        this.salesDocumentRepository = salesDocumentRepository;
        this.returnRequestRepository = returnRequestRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * @param amount what the note credits. Equal to the referenced document's total means the sale
     *               is annulled; less than it means an amount is corrected, which is what a partial
     *               refund is. The caller does not choose the SII reference code — it follows from
     *               this, and asking for it is only an invitation to get it wrong.
     * @param returnId the return this note settles, when there is one. The note is bound to it so a
     *                 refunded return can show the document that backs it.
     */
    @Transactional
    public SalesDocumentDto execute(UUID orderId,
                                    String folio,
                                    BigDecimal amount,
                                    String fileUrl,
                                    UUID returnId,
                                    UUID issuedBy) {
        SalesDocument referenced = salesDocumentRepository.findLiveByOrderId(orderId)
                .orElseThrow(() -> new DomainException(
                        "This sale has no live document to credit; there is nothing to undo"));

        if (salesDocumentRepository.existsByTypeAndFolio(
                SalesDocumentType.NOTA_CREDITO, folio == null ? "" : folio.trim())) {
            throw new DomainException("Folio " + folio + " is already registered");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new DomainException("A credit note needs an amount");
        }

        Money credited = Money.of(amount, referenced.getTotalAmount().currency());
        BigDecimal alreadyCredited = alreadyCreditedAgainst(referenced);
        BigDecimal remaining = referenced.getTotalAmount().amount().subtract(alreadyCredited);
        if (credited.amount().compareTo(remaining) > 0) {
            throw new DomainException(
                    "Document " + referenced.getFolio() + " has " + remaining
                            + " left to credit; " + credited.amount() + " would credit more than the sale");
        }

        SalesDocument note = SalesDocument.creditNote(
                orderId,
                folio,
                // The rate is the one the referenced document carries, not today's: a note undoes a
                // sale as it was declared, and the VAT rate could have moved since.
                TaxBreakdown.fromGross(credited, referenced.getTaxRate()),
                credited.amount().compareTo(remaining) == 0 && alreadyCredited.signum() == 0
                        ? SalesDocument.REFERENCE_ANNULS
                        : SalesDocument.REFERENCE_CORRECTS_AMOUNT,
                referenced,
                fileUrl,
                issuedBy);

        SalesDocument saved = salesDocumentRepository.save(note);

        if (returnId != null) {
            ReturnRequest request = returnRequestRepository.findById(returnId)
                    .orElseThrow(() -> new DomainException("Return not found: " + returnId));
            if (!request.getOrderId().equals(orderId)) {
                throw new DomainException("That return belongs to a different sale");
            }
            request.attachCreditNote(saved.getId());
            returnRequestRepository.save(request);
        }

        // After the commit, like every other document: the consumer reads the row back to compose
        // the customer's copy, and with Kafka it gets there before the transaction lands.
        eventPublisher.publish(new SalesDocumentIssued(
                saved.getId(), orderId, saved.getFolio(), Instant.now()));
        return SalesDocumentMapper.toDto(saved);
    }

    private BigDecimal alreadyCreditedAgainst(SalesDocument referenced) {
        List<SalesDocument> notes = salesDocumentRepository.findLiveCreditNotesFor(referenced.getId());
        return notes.stream()
                .map(note -> note.getTotalAmount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
