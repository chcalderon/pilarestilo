package com.pilarestilo.dispatch.domain.ports;

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
}
