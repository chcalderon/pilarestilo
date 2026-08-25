-- V87__category_variant_field_config.sql
-- Replaces the fixed 7-preset CategoryType variant-label system with
-- free-text, per-field-configurable labels. See
-- docs/superpowers/specs/2026-08-24-category-variant-field-config-design.md.
--
-- category_type / products.variant_type are NOT dropped here -- they stop
-- being read once the application code in this feature ships, and are
-- removed in a later, separate contract migration once nothing references
-- them (same expand/contract pattern as V76/V79 for net_amount/tax_amount).

ALTER TABLE categories
    ADD COLUMN defines_variant_fields BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN variant_field_config JSONB NULL;

ALTER TABLE categories
    ADD CONSTRAINT chk_categories_variant_field_config
    CHECK (
        (defines_variant_fields = FALSE AND variant_field_config IS NULL)
        OR (defines_variant_fields = TRUE AND variant_field_config IS NOT NULL)
    );

-- Backfill the 9 seeded categories from their current category_type, so
-- nothing changes visually until an admin edits it.
UPDATE categories SET defines_variant_fields = TRUE, variant_field_config = '{
    "primary":   {"label": "Color", "inputType": "FREE_TEXT", "allowMultiple": false, "allowCustom": true},
    "secondary": {"label": "Talla", "inputType": "OPTIONS",
                  "options": ["XS", "S", "M", "L", "XL", "XXL", "XXXL", "UNICO"],
                  "allowMultiple": true, "allowCustom": true}
}'::jsonb
WHERE slug IN ('vestidos', 'pantalones');

UPDATE categories SET defines_variant_fields = TRUE, variant_field_config = '{
    "primary":   {"label": "Color", "inputType": "FREE_TEXT", "allowMultiple": false, "allowCustom": true},
    "secondary": {"label": "Numero", "inputType": "RANGE", "min": 34, "max": 43,
                  "allowMultiple": true, "allowCustom": true}
}'::jsonb
WHERE slug = 'zapatos';

UPDATE categories SET defines_variant_fields = TRUE, variant_field_config = '{
    "primary":   {"label": "Material", "inputType": "FREE_TEXT", "allowMultiple": false, "allowCustom": true},
    "secondary": {"label": "Diseno", "inputType": "FREE_TEXT", "allowMultiple": false, "allowCustom": true}
}'::jsonb
WHERE slug = 'aros';

UPDATE categories SET defines_variant_fields = TRUE, variant_field_config = '{
    "primary":   {"label": "Variante", "inputType": "FREE_TEXT", "allowMultiple": false, "allowCustom": true},
    "secondary": {"label": "Detalle", "inputType": "FREE_TEXT", "allowMultiple": true, "allowCustom": true}
}'::jsonb
WHERE slug IN ('carteras', 'accesorios');

-- mujer, invierno, verano stay defines_variant_fields = FALSE (grouping),
-- variant_field_config NULL -- the default from the ALTER TABLE above.
