ALTER TABLE products
    ADD COLUMN list_price_amount NUMERIC(15,2),
    ADD COLUMN list_price_currency VARCHAR(10);

ALTER TABLE products
    ADD CONSTRAINT chk_products_list_price_positive
        CHECK (list_price_amount IS NULL OR list_price_amount > 0),
    ADD CONSTRAINT chk_products_list_price_currency
        CHECK ((list_price_amount IS NULL AND list_price_currency IS NULL)
            OR (list_price_amount IS NOT NULL AND list_price_currency IS NOT NULL)),
    ADD CONSTRAINT chk_products_list_price_gt_price
        CHECK (list_price_amount IS NULL OR list_price_amount > price_amount);
