-- V90__products_variant_template_id.sql
-- No backfill: every existing product starts with variant_template_id = NULL (generic
-- Variante/Detalle fallback) and is reassigned by hand -- explicit product decision, not
-- an oversight. See V89 for the referenced table.

ALTER TABLE products
    ADD COLUMN variant_template_id UUID NULL REFERENCES variant_templates(id);
