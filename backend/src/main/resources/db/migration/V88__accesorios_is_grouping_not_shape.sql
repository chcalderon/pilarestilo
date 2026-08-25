-- V88__accesorios_is_grouping_not_shape.sql
-- Fixes a V87 backfill defect: 'accesorios' was marked defines_variant_fields = TRUE
-- alongside its own child category 'aros' (also shape-defining, seeded in V60_1 with
-- accesorios as parent_id). Product 10000000-0000-0000-0000-000000000016 is seeded
-- (V61) under BOTH accesorios and aros -- entirely normal, since a category tree lets a
-- product carry a leaf and its ancestor together -- but ShapeCategoryResolver rejects any
-- product with 2+ shape categories, so simply reading that product started throwing.
--
-- 'accesorios' is a parent/container in the taxonomy (like mujer/invierno/verano), not a
-- shape in its own right -- 'aros' is the real shape underneath it. 'carteras' has no
-- shape-defining children and stays shape-defining.
UPDATE categories
SET defines_variant_fields = FALSE, variant_field_config = NULL
WHERE slug = 'accesorios';
