package com.pilarestilo.dispatch.application;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.application.dto.DispatchOrderSummaryDto;
import com.pilarestilo.dispatch.domain.ports.SalesDocumentGate;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Attaches to each dispatch the handful of facts that let a person recognise the order.
 *
 * <p>The queue used to show the first eight characters of the order's UUID and nothing else, so
 * somebody looking for a specific order had to open each row in turn, and somebody packing had no
 * idea what garment to reach for.
 *
 * <p>Two queries for the whole page regardless of its size: the orders, then the products those
 * orders mention. Enriching row by row would have been a query per dispatch on a screen whose whole
 * job is to show many at once.
 */
@Service
public class DispatchOrderSummaryService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final SalesDocumentGate salesDocumentGate;

    public DispatchOrderSummaryService(OrderRepository orderRepository,
                                       ProductRepository productRepository,
                                       SalesDocumentGate salesDocumentGate) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.salesDocumentGate = salesDocumentGate;
    }

    /**
     * Statuses in which an order has actually been paid for. Anything earlier must never be
     * packed: the money has not arrived.
     */
    private static final Set<OrderStatus> PAID_FOR = Set.of(
            OrderStatus.PAID, OrderStatus.PREPARING_ORDER, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    /**
     * The queue as somebody should work it: enriched, and without anything that was never paid for.
     *
     * <p>Five dispatches existed for orders still in CREATED. Taking one would have sent goods
     * nobody paid for, and the row gave no hint — it showed eight characters of a UUID. Creation is
     * now guarded on PAID in one place, so no new ones can appear; this keeps the ones already
     * there from being worked, without deleting rows somebody may still need to explain.
     */
    public List<DispatchDto> enrichWorkable(List<DispatchDto> dispatches) {
        return map(dispatches, true);
    }

    public List<DispatchDto> enrich(List<DispatchDto> dispatches) {
        return map(dispatches, false);
    }

    private List<DispatchDto> map(List<DispatchDto> dispatches, boolean onlyPaidFor) {
        if (dispatches.isEmpty()) {
            return dispatches;
        }
        Set<UUID> orderIds = dispatches.stream().map(DispatchDto::orderId).collect(Collectors.toSet());
        Map<UUID, Order> orders = orderRepository.findAllByIds(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));

        Set<UUID> productIds = orders.values().stream()
                .map(Order::getItems)
                .filter(items -> !items.isEmpty())
                .map(items -> items.getFirst().getProductId())
                .collect(Collectors.toSet());
        Map<UUID, String> imagesByProduct = productRepository.findAllByIds(productIds).stream()
                .filter(product -> product.getImageUrl() != null)
                .collect(Collectors.toMap(Product::getId, Product::getImageUrl));

        // Third query for the page, and only that: whoever works this queue should read why a row
        // cannot be taken, rather than find out by pressing a button that answers nothing.
        Set<UUID> blocked = salesDocumentGate.blockedAmong(orderIds);

        return dispatches.stream()
                .filter(dispatch -> !onlyPaidFor || isPaidFor(orders.get(dispatch.orderId())))
                .map(dispatch -> {
                    Order order = orders.get(dispatch.orderId());
                    // An order can be missing: DeleteOrder is a hard delete, and the dispatch row
                    // outlives it. In the history the dispatch is still shown, just without the
                    // summary, rather than disappearing from a list somebody is reading.
                    return order == null
                            ? dispatch
                            : dispatch.withOrderSummary(
                                    summarise(order, imagesByProduct, blocked.contains(order.getId())));
                })
                .toList();
    }

    private boolean isPaidFor(Order order) {
        return order != null && PAID_FOR.contains(order.getStatus());
    }

    private DispatchOrderSummaryDto summarise(Order order,
                                              Map<UUID, String> imagesByProduct,
                                              boolean needsSalesDocument) {
        List<OrderItem> items = order.getItems();
        OrderItem first = items.isEmpty() ? null : items.getFirst();
        return new DispatchOrderSummaryDto(
                order.getPublicReference(),
                items.size(),
                first == null ? null : first.getProductName(),
                first == null ? null : formatVariant(first),
                first == null ? null : imagesByProduct.get(first.getProductId()),
                order.getTotalAmount() == null ? null : order.getTotalAmount().amount(),
                order.getTotalAmount() == null ? null : order.getTotalAmount().currency(),
                needsSalesDocument
        );
    }

    /**
     * "Negro / M", or just the half that exists. Products with a single unnamed variant carry
     * placeholders rather than real choices, and printing "Base / UNICO" on a packing queue is
     * noise that hides the rows where the size actually matters.
     */
    private String formatVariant(OrderItem item) {
        String colour = meaningful(item.getVariantColor()) ? item.getVariantColor() : null;
        String size = meaningful(item.getVariantSize()) ? item.getVariantSize() : null;
        if (colour == null && size == null) {
            return null;
        }
        if (colour == null) {
            return size;
        }
        return size == null ? colour : colour + " / " + size;
    }

    private boolean meaningful(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalised = value.trim().toUpperCase();
        return !normalised.equals("BASE") && !normalised.equals("UNICO") && !normalised.equals("ÚNICO");
    }
}
