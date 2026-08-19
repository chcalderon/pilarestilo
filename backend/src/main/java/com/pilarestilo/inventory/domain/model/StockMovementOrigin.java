package com.pilarestilo.inventory.domain.model;

import java.util.UUID;

/**
 * What caused a stock movement.
 *
 * <p>The ledger has carried {@code reference_type}, {@code reference_id} and {@code recorded_by}
 * since V57 and every row written them null, so a shelf count that disagreed with the system could
 * be read line by line without ever learning which sale, return or hand correction moved the units.
 * A ledger that cannot answer "why" is a list, not an audit trail.
 *
 * <p>The factories are the whole point: they are the only causes this shop has, so a new one has to
 * be added here rather than invented at a call site as a loose string.
 */
public record StockMovementOrigin(
        String referenceType,
        UUID referenceId,
        UUID recordedBy
) {

    public static final String ORDER = "ORDER";
    public static final String RETURN_REQUEST = "RETURN_REQUEST";

    /** A customer's order: reserved at checkout, confirmed when paid, released when it falls. */
    public static StockMovementOrigin forOrder(UUID orderId) {
        return new StockMovementOrigin(ORDER, orderId, null);
    }

    /** A return putting a garment back on the shelf, and who decided it was fit to sell again. */
    public static StockMovementOrigin forReturn(UUID returnId, UUID decidedBy) {
        return new StockMovementOrigin(RETURN_REQUEST, returnId, decidedBy);
    }

    /**
     * No cause recorded. Kept for the paths that genuinely have none to give — nothing else may use
     * it, because a movement with no origin is the state this class exists to end.
     */
    public static StockMovementOrigin unknown() {
        return new StockMovementOrigin(null, null, null);
    }
}
