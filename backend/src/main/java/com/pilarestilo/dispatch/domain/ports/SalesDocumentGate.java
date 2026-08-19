package com.pilarestilo.dispatch.domain.ports;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Whether an order may leave the warehouse.
 *
 * <p>The boleta accompanies the goods, so the checkpoint sits where the goods move, not where the
 * money arrives. Dispatch asks the question; billing answers it, along with whether the shop has the
 * rule switched on at all.
 */
public interface SalesDocumentGate {

    /** True when the order has no live tax document and the shop requires one before dispatch. */
    boolean blocksDispatch(UUID orderId);

    /**
     * Which of these orders the gate would refuse. Asked in one go because the dispatch queue needs
     * the answer for every row it draws, and a question per row would undo the two queries that
     * screen is built on.
     */
    Set<UUID> blockedAmong(Collection<UUID> orderIds);
}
