INSERT INTO categories (
    id,
    slug,
    name_es,
    name_en,
    parent_id,
    sort_order,
    active,
    image_url
)
SELECT
    '58df2aea-c3ca-43dd-92a3-f7c3d0dd1e99',
    'aros',
    'Aros',
    'Earrings',
    '30000000-0000-0000-0000-000000000008',
    1,
    TRUE,
    'https://images.unsplash.com/photo-1617038220319-276d3cfab638?w=900&q=80'
WHERE NOT EXISTS (
    SELECT 1
    FROM categories
    WHERE id = '58df2aea-c3ca-43dd-92a3-f7c3d0dd1e99'
       OR slug = 'aros'
);
