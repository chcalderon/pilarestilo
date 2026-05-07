ALTER TABLE product_variants
    ALTER COLUMN size TYPE VARCHAR(32);

ALTER TABLE product_size_stocks
    ALTER COLUMN size TYPE VARCHAR(32);
