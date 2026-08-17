package com.pilarestilo.billing.domain;

import com.pilarestilo.order.domain.enums.OrderStatus;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which orders can carry a tax document: those whose money is already in.
 *
 * <p>Anything earlier has no sale to declare, and a cancelled order has one that was undone.
 *
 * <p>The set is written once and the SQL list is derived from it. Stated twice — once in the use
 * case that refuses, once in the query that offers — they drift, and the pending queue starts
 * listing sales the use case then rejects.
 */
public final class DocumentableSale {

    public static final Set<OrderStatus> STATUSES = new LinkedHashSet<>(Set.of(
            OrderStatus.PAID,
            OrderStatus.PREPARING_ORDER,
            OrderStatus.SHIPPED,
            OrderStatus.DELIVERED));

    private DocumentableSale() {}

    public static boolean allows(OrderStatus status) {
        return STATUSES.contains(status);
    }

    /** The same set as a SQL {@code IN} list, quoted and parenthesised. */
    public static String sqlInList() {
        return STATUSES.stream()
                .map(status -> "'" + status.name() + "'")
                .collect(Collectors.joining(",", "(", ")"));
    }
}
