package com.pilarestilo.billing.application.usecases;

import com.pilarestilo.billing.application.dto.SalesDocumentDto;
import com.pilarestilo.billing.application.mappers.SalesDocumentMapper;
import com.pilarestilo.billing.domain.DocumentableSale;
import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.events.SalesDocumentIssued;
import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.shared.application.TaxBreakdown;
import com.pilarestilo.shared.application.AfterCommitPublisher;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers the boleta or factura backing a sale.
 *
 * <p>The document is produced outside this system — by hand in the SII's eBoleta app — so what is
 * recorded is the folio the shop was given, the amounts the sale carries and, optionally, the file.
 * Nothing here talks to the SII.
 */
@Service
public class IssueSalesDocumentUseCase {

    private final SalesDocumentRepository salesDocumentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AfterCommitPublisher eventPublisher;

    public IssueSalesDocumentUseCase(SalesDocumentRepository salesDocumentRepository,
                                     OrderRepository orderRepository,
                                     UserRepository userRepository,
                                     AfterCommitPublisher eventPublisher) {
        this.salesDocumentRepository = salesDocumentRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    // The call to issue() below runs via 'this', bypassing the proxy that would normally start
    // its own @Transactional -- harmless here, because this method's own @Transactional is what
    // callers go through, and by the time this line runs that transaction is already active.
    // S107: one parameter per column a boleta/factura actually carries; a DTO wrapper here would
    // just move the same fields one level out, not reduce them.
    @SuppressWarnings({"java:S6809", "java:S107"})
    @Transactional
    public SalesDocumentDto execute(UUID orderId,
                                    SalesDocumentType type,
                                    String folio,
                                    String receiverRut,
                                    String receiverBusinessName,
                                    String receiverBusinessActivity,
                                    String fileUrl,
                                    UUID issuedBy) {
        return issue(orderId, type, folio, receiverRut, receiverBusinessName, receiverBusinessActivity,
                fileUrl, issuedBy, null);
    }

    /**
     * @param replacesDocumentId the document this one corrects, already voided by the caller. Only
     *                           {@link ReissueSalesDocumentUseCase} passes it.
     */
    @SuppressWarnings("java:S107")
    @Transactional
    public SalesDocumentDto issue(UUID orderId,
                                  SalesDocumentType type,
                                  String folio,
                                  String receiverRut,
                                  String receiverBusinessName,
                                  String receiverBusinessActivity,
                                  String fileUrl,
                                  UUID issuedBy,
                                  UUID replacesDocumentId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new DomainException("Order not found: " + orderId));

        if (!DocumentableSale.allows(order.getStatus())) {
            throw new DomainException(
                    "Order " + order.getPublicReference() + " is " + order.getStatus()
                            + "; a tax document is only issued for a paid sale");
        }
        salesDocumentRepository.findLiveByOrderId(orderId).ifPresent(existing -> {
            throw new DomainException("Order " + order.getPublicReference()
                    + " already has document " + existing.getFolio() + "; void it before issuing another");
        });
        if (salesDocumentRepository.existsByTypeAndFolio(type, folio == null ? "" : folio.trim())) {
            throw new DomainException("Folio " + folio + " is already registered");
        }

        // An external sale (V94) has no linked account -- order.getCustomerId() is then null, and
        // findById(null) throws rather than returning empty. Every boleta issued for an
        // off-platform sale hit this before the guard existed.
        Optional<User> buyer = order.getCustomerId() == null
                ? Optional.empty()
                : userRepository.findById(order.getCustomerId());

        SalesDocument document = SalesDocument.issue(
                orderId,
                type,
                folio,
                // Recomputed from the order's own total and its snapshotted rate, so the document
                // cannot disagree with the sale it declares.
                TaxBreakdown.fromGross(order.getTotalAmount(), order.getTaxRate()),
                receiverRut,
                receiverBusinessName,
                receiverBusinessActivity,
                // No account to name means no email to send to either, but the free-text buyer
                // name from the sale itself (V94) is still worth snapshotting onto the document --
                // "who this was sold to" should not go blank just because it was a walk-in sale.
                buyer.map(User::getFullName).orElse(order.getBuyerName()),
                buyer.map(User::getEmail).orElse(null),
                fileUrl,
                issuedBy,
                replacesDocumentId);

        SalesDocument saved = salesDocumentRepository.save(document);
        // Published after the commit, not inside it. BillingNotificationDispatcher reads the
        // document back to compose the email, and with Kafka the consumer gets there first:
        // announced from inside the transaction it finds no row and logs "could not be read back",
        // so the customer never hears that their boleta exists. Same race the reviews summary hit.
        eventPublisher.publish(new SalesDocumentIssued(
                saved.getId(), orderId, saved.getFolio(), Instant.now()));
        return SalesDocumentMapper.toDto(saved);
    }
}
