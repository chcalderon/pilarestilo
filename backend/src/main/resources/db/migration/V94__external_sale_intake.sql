-- External sale intake (Fase 2, Increment F). A sale made off-platform (Instagram / Facebook /
-- WhatsApp, later POS / MercadoLibre) becomes a real paid order with no registered customer.

-- customer_id is NOT NULL from V1. An external sale has no account behind it. The FK to users(id)
-- stays and still enforces on non-null values.
ALTER TABLE orders ALTER COLUMN customer_id DROP NOT NULL;

ALTER TABLE orders
    ADD COLUMN delivery_method VARCHAR(16) NOT NULL DEFAULT 'SHIPPING',
    ADD COLUMN buyer_name VARCHAR(160),
    ADD COLUMN buyer_contact VARCHAR(160),
    ADD COLUMN external_idempotency_key VARCHAR(64);

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_delivery_method CHECK (delivery_method IN ('SHIPPING', 'PICKUP'));

-- One order per client-supplied key. NULL for web orders, so a partial index.
CREATE UNIQUE INDEX uq_orders_external_idempotency_key
    ON orders (external_idempotency_key)
    WHERE external_idempotency_key IS NOT NULL;

INSERT INTO permissions (code, name, description, module, category) VALUES
    ('orders.create', 'Registrar venta',
     'Registrar una venta hecha fuera del sitio (redes, mostrador)', 'orders', 'write')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission_grants (role, permission_code) VALUES
    ('ADMIN', 'orders.create'),
    ('SELLER', 'orders.create')
ON CONFLICT DO NOTHING;
