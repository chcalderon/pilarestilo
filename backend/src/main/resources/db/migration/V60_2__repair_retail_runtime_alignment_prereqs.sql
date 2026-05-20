-- Repair legacy catalog prerequisites required by V61 runtime alignment.
-- Some environments already had an "aros" category with a different UUID and
-- are also missing seed products that V61 expects to re-variantize.

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
    'aros-runtime-repair',
    'Aros',
    'Earrings',
    '30000000-0000-0000-0000-000000000008',
    1,
    TRUE,
    'https://images.unsplash.com/photo-1617038220319-276d3cfab638?w=900&q=80'
WHERE EXISTS (
    SELECT 1
    FROM categories
    WHERE slug = 'aros'
      AND id <> '58df2aea-c3ca-43dd-92a3-f7c3d0dd1e99'
)
  AND NOT EXISTS (
    SELECT 1
    FROM categories
    WHERE id = '58df2aea-c3ca-43dd-92a3-f7c3d0dd1e99'
);

UPDATE product_categories
SET category_id = '58df2aea-c3ca-43dd-92a3-f7c3d0dd1e99'
WHERE category_id IN (
    SELECT id
    FROM categories
    WHERE slug = 'aros'
      AND id <> '58df2aea-c3ca-43dd-92a3-f7c3d0dd1e99'
)
  AND EXISTS (
    SELECT 1
    FROM categories
    WHERE id = '58df2aea-c3ca-43dd-92a3-f7c3d0dd1e99'
);

DELETE FROM categories
WHERE slug = 'aros'
  AND id <> '58df2aea-c3ca-43dd-92a3-f7c3d0dd1e99';

UPDATE categories
SET
    slug = 'aros',
    name_es = 'Aros',
    name_en = 'Earrings',
    parent_id = '30000000-0000-0000-0000-000000000008',
    sort_order = 1,
    active = TRUE,
    image_url = 'https://images.unsplash.com/photo-1617038220319-276d3cfab638?w=900&q=80'
WHERE id = '58df2aea-c3ca-43dd-92a3-f7c3d0dd1e99';

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
);

INSERT INTO products (
    id,
    name,
    description,
    price_amount,
    price_currency,
    list_price_amount,
    list_price_currency,
    image_url,
    condition,
    brand,
    stock,
    active,
    shipping_origin_zone
)
VALUES
    (
        '10000000-0000-0000-0000-000000000004',
        'Pumps Stiletto Nude',
        'Zapatos stiletto en cuero genuino color nude, taco 10cm. Talla 37. Usados dos veces.',
        120000.00,
        'CLP',
        144000.00,
        'CLP',
        '/api/media/products/product-004.jpg',
        'USED',
        'Valentino',
        1,
        TRUE,
        'LOCAL'
    ),
    (
        '10000000-0000-0000-0000-000000000005',
        'Vestido Cocktail Rojo',
        'Vestido de coctel en rojo intenso, espalda cruzada. Coleccion exclusiva.',
        320000.00,
        'CLP',
        384000.00,
        'CLP',
        '/api/media/products/product-005.jpg',
        'NEW',
        'Carolina Herrera',
        2,
        TRUE,
        'LOCAL'
    ),
    (
        '10000000-0000-0000-0000-000000000010',
        'Traje Pantalon Marino',
        'Traje pantalon en lana fria marino, chaqueta slim fit con pantalon de tiro alto. Talla 40.',
        380000.00,
        'CLP',
        456000.00,
        'CLP',
        '/api/media/products/product-010.jpg',
        'NEW',
        'Carolina Herrera',
        2,
        TRUE,
        'LOCAL'
    ),
    (
        '10000000-0000-0000-0000-000000000012',
        'Botines Cuero Negro',
        'Botines de cuero negro con taco medio y punta afinada. Ideal para look urbano en temporada fria.',
        138000.00,
        'CLP',
        165000.00,
        'CLP',
        '/api/media/products/product-012.jpg',
        'NEW',
        'Michael Kors',
        3,
        TRUE,
        'LOCAL'
    ),
    (
        '10000000-0000-0000-0000-000000000014',
        'Panuelo Seda Estampado',
        'Panuelo de seda estampado multicolor, terminado a mano. Se puede usar al cuello o como accesorio de bolso.',
        79000.00,
        'CLP',
        95000.00,
        'CLP',
        '/api/media/products/product-014.jpg',
        'NEW',
        'Hermes',
        5,
        TRUE,
        'NACIONAL'
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_categories (product_id, category_id)
VALUES
    ('10000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000006'),
    ('10000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000004'),
    ('10000000-0000-0000-0000-000000000010', '30000000-0000-0000-0000-000000000005'),
    ('10000000-0000-0000-0000-000000000010', '30000000-0000-0000-0000-000000000002'),
    ('10000000-0000-0000-0000-000000000012', '30000000-0000-0000-0000-000000000006'),
    ('10000000-0000-0000-0000-000000000012', '30000000-0000-0000-0000-000000000002'),
    ('10000000-0000-0000-0000-000000000014', '30000000-0000-0000-0000-000000000008')
ON CONFLICT DO NOTHING;
