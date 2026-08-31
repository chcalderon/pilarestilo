-- Contract half of V69 (products.variant_type) and V87 (categories.defines_variant_fields /
-- variant_field_config).
--
-- Both stopped being read when the varianttemplate module shipped (V89/V90): a product's variant
-- field configuration now resolves from its assigned template
-- (docs/superpowers/plans/2026-08-26-variant-templates.md), not from these columns.
-- products.variant_type was never backfilled and never wired to anything after V69.
--
-- Verified 2026-08-31: no JPA entity maps these columns, no code path (backend or frontend) reads
-- them, no index or view depends on them, and notification-service's read-only entities do not map
-- products or categories. The two CHECK constraints (chk_products_variant_type,
-- chk_categories_variant_field_config) are dropped automatically with their columns.
--
-- categories.category_type is NOT dropped here: the V87 header grouped it with variant_type, but it
-- stayed in use (Category domain/entity/DTO, navigation, publication).

ALTER TABLE products
    DROP COLUMN variant_type;

ALTER TABLE categories
    DROP COLUMN defines_variant_fields,
    DROP COLUMN variant_field_config;
