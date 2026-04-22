CREATE TABLE IF NOT EXISTS product_variants (
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    color      VARCHAR(80) NOT NULL,
    size       VARCHAR(8) NOT NULL,
    stock      INT NOT NULL DEFAULT 0 CHECK (stock >= 0),
    PRIMARY KEY (product_id, color, size)
);

CREATE INDEX IF NOT EXISTS idx_product_variants_product
    ON product_variants(product_id);

INSERT INTO product_variants (product_id, color, size, stock)
SELECT product_id, 'Base', size, stock
FROM product_size_stocks
ON CONFLICT (product_id, color, size) DO NOTHING;
