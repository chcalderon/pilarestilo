package com.pilarestilo.billing.application.usecases;

import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Closes a sale as cancelled: its tax document is voided and the order is cancelled, together.
 *
 * <p>Two separate calls would leave a window where the boleta is void but the sale still counts as
 * live — a sale nobody has declared and nobody has undone — and if the second call failed the shop
 * would be left in it. Here they commit or they do not.
 *
 * <p>Cancelling the order is what puts the units back on the shelf and releases the discount, both
 * through the single hook in {@link UpdateOrderStatusUseCase}. Nothing about inventory is repeated
 * here; doing it in two places is how the effects used to be applied twice.
 *
 * <p>Voiding a document without cancelling the sale stays a separate action, because correcting a
 * folio is not undoing a sale. That distinction is the reason this use case exists rather than a
 * flag on {@link VoidSalesDocumentUseCase}.
 */
@Service
public class CancelSaleUseCase {

    private final SalesDocumentRepository salesDocumentRepository;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;

    public CancelSaleUseCase(SalesDocumentRepository salesDocumentRepository,
                             UpdateOrderStatusUseCase updateOrderStatusUseCase) {
        this.salesDocumentRepository = salesDocumentRepository;
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
    }

    @Transactional
    public OrderDto execute(UUID orderId, String reason, UUID actorId) {
        if (reason == null || reason.isBlank()) {
            throw new DomainException("Cancelling a sale requires a reason");
        }
        if (actorId == null) {
            throw new DomainException("Cancelling a sale requires the user who did it");
        }

        // A sale with no document cancels just the same: an order can be paid and not yet declared,
        // which is exactly the state the pending queue lists.
        Optional<SalesDocument> live = salesDocumentRepository.findLiveByOrderId(orderId);
        live.ifPresent(document -> {
            document.voidDocument(reason, actorId);
            salesDocumentRepository.save(document);
        });

        return updateOrderStatusUseCase.execute(orderId, OrderStatus.CANCELLED);
    }
}
