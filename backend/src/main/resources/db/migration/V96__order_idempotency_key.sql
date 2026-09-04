-- A refresh mid-checkout, or a fast double-click racing the frontend's disabled state, used to
-- reach CreateOrderUseCase twice and create two real orders -- reserving stock and redeeming a
-- discount code both times, with nothing to catch it server-side. This is the same shape of
-- problem V94's external_idempotency_key solved for off-platform sales, but that column is
-- documented as external-only; this is its counterpart for the web checkout, kept separate so
-- neither flow's column can be misread as covering the other.

ALTER TABLE orders
    ADD COLUMN idempotency_key VARCHAR(64);

-- One order per client-supplied key. NULL for every order created before this column existed (and
-- for any future caller that legitimately has none), so a partial index.
CREATE UNIQUE INDEX uq_orders_idempotency_key
    ON orders (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
