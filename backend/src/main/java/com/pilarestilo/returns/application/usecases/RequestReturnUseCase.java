package com.pilarestilo.returns.application.usecases;

import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.returns.application.dto.ReturnRequestDto;
import com.pilarestilo.returns.application.mappers.ReturnRequestMapper;
import com.pilarestilo.returns.domain.RetractoWindow;
import com.pilarestilo.returns.domain.enums.ReturnKind;
import com.pilarestilo.returns.domain.enums.ReturnStatus;
import com.pilarestilo.returns.domain.model.ReturnRequest;
import com.pilarestilo.returns.domain.ports.ReturnRequestRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * Opens a return, from either door: the customer retracting or the shop taking a garment back.
 *
 * <p>The validation differs by door and that is the whole point of the distinction. A
 * {@link ReturnKind#RETRACTO} is only valid while the legal window is open — and once opened it can
 * never be refused. A {@link ReturnKind#DEVOLUCION} the shop opens by agreement, at any time after
 * delivery, and may still reject.
 */
@Service
public class RequestReturnUseCase {

    private final ReturnRequestRepository returnRequestRepository;
    private final OrderRepository orderRepository;
    private final DispatchRepository dispatchRepository;

    public RequestReturnUseCase(ReturnRequestRepository returnRequestRepository,
                                OrderRepository orderRepository,
                                DispatchRepository dispatchRepository) {
        this.returnRequestRepository = returnRequestRepository;
        this.orderRepository = orderRepository;
        this.dispatchRepository = dispatchRepository;
    }

    /**
     * @param requestedBy the customer when she opens it; null when the shop does
     * @param enforceOwnership true for the customer-facing route, so one buyer cannot open a return
     *                         against another's order
     */
    @Transactional
    public ReturnRequestDto execute(UUID orderId, ReturnKind kind, String reason,
                                    UUID requestedBy, boolean enforceOwnership) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new DomainException("Order not found: " + orderId));

        if (enforceOwnership && !order.getCustomerId().equals(requestedBy)) {
            throw new DomainException("You can only return your own orders");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new DomainException(
                    "Order " + order.getPublicReference() + " has not been delivered; "
                            + "an undelivered sale is cancelled, not returned");
        }
        returnRequestRepository.findOpenByOrderId(orderId).ifPresent(existing -> {
            throw new DomainException(
                    "Order " + order.getPublicReference() + " already has a return in progress");
        });
        /*
         * And a sale whose money already went back cannot be undone again. The partial index only
         * guards the open ones, which is right for a rejected return -- that one can be reopened by
         * agreement. A refunded one is different: the sale is already undone, and letting a second
         * return through would put the shop one click away from paying for the same garment twice.
         */
        boolean alreadyRefunded = returnRequestRepository.findAllByOrderId(orderId).stream()
                .anyMatch(previous -> previous.getStatus() == ReturnStatus.REFUNDED);
        if (alreadyRefunded) {
            throw new DomainException(
                    "Order " + order.getPublicReference() + " was already refunded");
        }

        if (kind == ReturnKind.RETRACTO) {
            Instant deliveredAt = deliveredAt(orderId);
            if (!RetractoWindow.isOpen(deliveredAt, Instant.now())) {
                throw new DomainException(
                        "The ten-day window to retract order " + order.getPublicReference()
                                + " has closed. A return outside it is by agreement with the shop.");
            }
        }

        ReturnRequest saved = returnRequestRepository.save(
                ReturnRequest.open(orderId, kind, reason, requestedBy));
        return ReturnRequestMapper.toDto(saved);
    }

    /**
     * The clock starts when the customer received the goods, which is the dispatch's delivery
     * timestamp — not when the order was placed and not when it shipped.
     */
    private Instant deliveredAt(UUID orderId) {
        return dispatchRepository.findByOrderId(orderId)
                .map(Dispatch::getDeliveredAt)
                .map(local -> local.atZone(ZoneId.systemDefault()).toInstant())
                .orElse(null);
    }

    /** What the storefront needs to decide whether to offer the button at all. */
    @Transactional(readOnly = true)
    public Optional<Instant> retractoClosesAt(UUID orderId) {
        return Optional.ofNullable(RetractoWindow.closesAt(deliveredAt(orderId)));
    }
}
