-- Enable trigram extension for partial-match search on product name and brand
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_products_name_trgm
    ON products USING gin (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_products_brand_trgm
    ON products USING gin (brand gin_trgm_ops);
