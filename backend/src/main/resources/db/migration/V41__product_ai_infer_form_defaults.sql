ALTER TABLE system_settings
    ADD COLUMN IF NOT EXISTS product_ai_infer_default_brand VARCHAR(120),
    ADD COLUMN IF NOT EXISTS product_ai_infer_default_condition VARCHAR(10),
    ADD COLUMN IF NOT EXISTS product_ai_infer_base_price INTEGER,
    ADD COLUMN IF NOT EXISTS product_ai_infer_list_price_multiplier NUMERIC(6,2);

UPDATE system_settings
SET
    product_ai_infer_default_brand = COALESCE(NULLIF(product_ai_infer_default_brand, ''), 'Pilar Estilo'),
    product_ai_infer_default_condition = COALESCE(NULLIF(product_ai_infer_default_condition, ''), 'USED'),
    product_ai_infer_base_price = COALESCE(product_ai_infer_base_price, 24990),
    product_ai_infer_list_price_multiplier = COALESCE(product_ai_infer_list_price_multiplier, 1.35);
