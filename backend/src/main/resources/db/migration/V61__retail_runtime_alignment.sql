-- Align storefront/admin runtime metadata with the migrated frontend.
-- This keeps the catalog metadata-driven and seeds realistic variants for QA.

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
    '30000000-0000-0000-0000-000000000009',
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
    WHERE slug = 'aros'
);

UPDATE categories
SET
    menu_visible = TRUE,
    category_type = CASE slug
        WHEN 'invierno' THEN 'SEASON'
        WHEN 'verano' THEN 'SEASON'
        WHEN 'vestidos' THEN 'CLOTHING'
        WHEN 'pantalones' THEN 'CLOTHING'
        WHEN 'zapatos' THEN 'SHOES'
        WHEN 'carteras' THEN 'ACCESSORY'
        WHEN 'accesorios' THEN 'ACCESSORY'
        WHEN 'aros' THEN 'JEWELRY'
        ELSE category_type
    END,
    hero_image_url = CASE
        WHEN slug IN ('mujer', 'zapatos', 'accesorios') AND hero_image_url IS NULL THEN image_url
        ELSE hero_image_url
    END
WHERE slug IN (
    'mujer',
    'invierno',
    'verano',
    'vestidos',
    'pantalones',
    'zapatos',
    'carteras',
    'accesorios',
    'aros'
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
VALUES (
    '10000000-0000-0000-0000-000000000016',
    'Aros Perla Dorados',
    'Aros de bisuteria premium con bano dorado y perlas sinteticas. Livianos, elegantes y pensados para looks de noche.',
    69000.00,
    'CLP',
    83000.00,
    'CLP',
    '/api/media/products/product-014.jpg',
    'NEW',
    'Pilar Estilo Atelier',
    3,
    TRUE,
    'LOCAL'
)
ON CONFLICT (id) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    price_amount = EXCLUDED.price_amount,
    price_currency = EXCLUDED.price_currency,
    list_price_amount = EXCLUDED.list_price_amount,
    list_price_currency = EXCLUDED.list_price_currency,
    image_url = EXCLUDED.image_url,
    condition = EXCLUDED.condition,
    brand = EXCLUDED.brand,
    active = EXCLUDED.active,
    shipping_origin_zone = EXCLUDED.shipping_origin_zone;

INSERT INTO product_categories (product_id, category_id)
VALUES
    ('10000000-0000-0000-0000-000000000016', '30000000-0000-0000-0000-000000000008'),
    ('10000000-0000-0000-0000-000000000016', '30000000-0000-0000-0000-000000000009')
ON CONFLICT DO NOTHING;

DELETE FROM product_variants
WHERE product_id IN (
    '10000000-0000-0000-0000-000000000004',
    '10000000-0000-0000-0000-000000000005',
    '10000000-0000-0000-0000-000000000010',
    '10000000-0000-0000-0000-000000000012',
    '10000000-0000-0000-0000-000000000014',
    '10000000-0000-0000-0000-000000000016'
);

INSERT INTO product_variants (product_id, color, size, stock_on_hand, stock_reserved)
VALUES
    ('10000000-0000-0000-0000-000000000004', 'Nude', '37', 1, 0),
    ('10000000-0000-0000-0000-000000000004', 'Nude', '38', 0, 0),
    ('10000000-0000-0000-0000-000000000005', 'Rojo', 'S', 1, 0),
    ('10000000-0000-0000-0000-000000000005', 'Rojo', 'M', 1, 0),
    ('10000000-0000-0000-0000-000000000005', 'Rojo', 'L', 0, 0),
    ('10000000-0000-0000-0000-000000000010', 'Marino', '38', 1, 0),
    ('10000000-0000-0000-0000-000000000010', 'Marino', '40', 1, 0),
    ('10000000-0000-0000-0000-000000000012', 'Negro', '38', 1, 0),
    ('10000000-0000-0000-0000-000000000012', 'Negro', '39', 1, 0),
    ('10000000-0000-0000-0000-000000000012', 'Negro', '40', 0, 0),
    ('10000000-0000-0000-0000-000000000014', 'Multicolor', 'Panuelo 90', 3, 0),
    ('10000000-0000-0000-0000-000000000016', 'Bano Oro 18K', 'Perla', 2, 0),
    ('10000000-0000-0000-0000-000000000016', 'Bano Oro 18K', 'Eslabon', 1, 0);

DELETE FROM product_size_stocks
WHERE product_id IN (
    '10000000-0000-0000-0000-000000000004',
    '10000000-0000-0000-0000-000000000005',
    '10000000-0000-0000-0000-000000000010',
    '10000000-0000-0000-0000-000000000012',
    '10000000-0000-0000-0000-000000000014',
    '10000000-0000-0000-0000-000000000016'
);

INSERT INTO product_size_stocks (product_id, size, stock)
SELECT product_id, size, SUM(stock_on_hand - stock_reserved)::INT
FROM product_variants
WHERE product_id IN (
    '10000000-0000-0000-0000-000000000004',
    '10000000-0000-0000-0000-000000000005',
    '10000000-0000-0000-0000-000000000010',
    '10000000-0000-0000-0000-000000000012',
    '10000000-0000-0000-0000-000000000014',
    '10000000-0000-0000-0000-000000000016'
)
GROUP BY product_id, size;

UPDATE products p
SET
    stock = totals.available,
    updated_at = NOW()
FROM (
    SELECT product_id, COALESCE(SUM(stock_on_hand - stock_reserved), 0)::INT AS available
    FROM product_variants
    WHERE product_id IN (
        '10000000-0000-0000-0000-000000000004',
        '10000000-0000-0000-0000-000000000005',
        '10000000-0000-0000-0000-000000000010',
        '10000000-0000-0000-0000-000000000012',
        '10000000-0000-0000-0000-000000000014',
        '10000000-0000-0000-0000-000000000016'
    )
    GROUP BY product_id
) totals
WHERE p.id = totals.product_id;

INSERT INTO navigation_sections (
    id,
    root_category_id,
    layout,
    column_count,
    banner_image_url,
    banner_title,
    banner_subtitle,
    banner_link,
    active,
    sort_order,
    created_at,
    updated_at
)
SELECT
    uuid_generate_v4(),
    '30000000-0000-0000-0000-000000000001',
    'FEATURED_GRID',
    4,
    'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=1200&q=80',
    'Edicion Curada',
    'Invierno, zapatos y accesorios con lectura editorial',
    NULL,
    TRUE,
    0,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM navigation_sections
    WHERE root_category_id = '30000000-0000-0000-0000-000000000001'
);

UPDATE navigation_sections
SET
    layout = 'FEATURED_GRID',
    column_count = 4,
    banner_image_url = 'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=1200&q=80',
    banner_title = 'Edicion Curada',
    banner_subtitle = 'Invierno, zapatos y accesorios con lectura editorial',
    banner_link = NULL,
    updated_at = NOW()
WHERE root_category_id = '30000000-0000-0000-0000-000000000001';
