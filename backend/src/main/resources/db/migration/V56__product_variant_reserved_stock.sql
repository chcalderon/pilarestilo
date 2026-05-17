ALTER TABLE product_variants RENAME COLUMN stock TO stock_on_hand;
ALTER TABLE product_variants ADD COLUMN stock_reserved INT NOT NULL DEFAULT 0 CHECK (stock_reserved >= 0);
ALTER TABLE product_variants ADD CONSTRAINT chk_variant_reserved_le_on_hand CHECK (stock_reserved <= stock_on_hand);

-- Sync products.stock to reflect available (on_hand - reserved) for products with variants
UPDATE products p
   SET stock = totals.available
  FROM (
    SELECT product_id, SUM(stock_on_hand - stock_reserved) AS available
      FROM product_variants
     GROUP BY product_id
  ) totals
 WHERE p.id = totals.product_id;
