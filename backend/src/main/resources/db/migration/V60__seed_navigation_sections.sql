-- Bootstrap mega menu: create a NavigationSection for every root category (parent_id IS NULL).
-- Idempotent via ON CONFLICT — safe to run multiple times.
-- Admin can later customize layout/banner via /admin/navegacion.
INSERT INTO navigation_sections (id, root_category_id, layout, column_count, active, sort_order, created_at, updated_at)
SELECT
    uuid_generate_v4(),
    id,
    'COLUMNS',
    4,
    true,
    sort_order,
    NOW(),
    NOW()
FROM categories
WHERE parent_id IS NULL
  AND active = true
ON CONFLICT (root_category_id) DO NOTHING;
