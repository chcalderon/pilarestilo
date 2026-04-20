-- Add shipping origin zone to products
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS shipping_origin_zone VARCHAR(16) NOT NULL DEFAULT 'CABA';

-- Per-size stock child table
CREATE TABLE IF NOT EXISTS product_size_stocks (
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    size       VARCHAR(8) NOT NULL,
    stock      INT NOT NULL DEFAULT 0 CHECK (stock >= 0),
    PRIMARY KEY (product_id, size)
);

-- Backfill: one UNICO row per existing product preserving current stock
INSERT INTO product_size_stocks (product_id, size, stock)
SELECT id, 'UNICO', stock
FROM products
ON CONFLICT (product_id, size) DO NOTHING;
