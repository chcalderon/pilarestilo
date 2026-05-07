-- Normalize stock source of truth to variant rows.
-- 1) Ensure every product has at least one variant row.
INSERT INTO product_variants (product_id, color, size, stock)
SELECT s.product_id, 'Base', s.size, s.stock
FROM product_size_stocks s
WHERE NOT EXISTS (
    SELECT 1 FROM product_variants v WHERE v.product_id = s.product_id
);

INSERT INTO product_variants (product_id, color, size, stock)
SELECT p.id, 'Base', 'UNICO', p.stock
FROM products p
WHERE NOT EXISTS (
    SELECT 1 FROM product_variants v WHERE v.product_id = p.id
);

-- 2) Rebuild size stocks from variants.
DELETE FROM product_size_stocks
WHERE product_id IN (SELECT DISTINCT product_id FROM product_variants);

INSERT INTO product_size_stocks (product_id, size, stock)
SELECT v.product_id, v.size, SUM(v.stock)::INT
FROM product_variants v
GROUP BY v.product_id, v.size;

-- 3) Rebuild product stock from variant sum.
UPDATE products p
SET stock = totals.total_stock
FROM (
    SELECT product_id, COALESCE(SUM(stock), 0)::INT AS total_stock
    FROM product_variants
    GROUP BY product_id
) totals
WHERE p.id = totals.product_id;
