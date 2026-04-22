-- Add 5 additional catalog products for storefront/admin pagination and demos.

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
        '10000000-0000-0000-0000-000000000011',
        'Bolso Hobo Piel Negra',
        'Bolso hobo de piel suave color negro con herrajes dorados. Interior amplio y cierre magnetico.',
        265000.00,
        'CLP',
        318000.00,
        'CLP',
        '/api/media/products/product-011.jpg',
        'USED',
        'Celine',
        2,
        TRUE,
        'SANTIAGO'
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
        'RM'
    ),
    (
        '10000000-0000-0000-0000-000000000013',
        'Falda Satin Champagne',
        'Falda satinada midi en tono champagne con caida fluida. Pieza versatil para eventos y oficina.',
        112000.00,
        'CLP',
        134000.00,
        'CLP',
        '/api/media/products/product-013.jpg',
        'NEW',
        'Massimo Dutti',
        4,
        TRUE,
        'SANTIAGO'
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
        'REGIONES'
    ),
    (
        '10000000-0000-0000-0000-000000000015',
        'Abrigo Lana Camel',
        'Abrigo largo de lana color camel con corte recto y forro interior. Prenda premium para invierno.',
        345000.00,
        'CLP',
        414000.00,
        'CLP',
        '/api/media/products/product-015.jpg',
        'USED',
        'Max Mara',
        1,
        TRUE,
        'SANTIAGO'
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_size_stocks (product_id, size, stock)
VALUES
    ('10000000-0000-0000-0000-000000000011', 'UNICO', 2),
    ('10000000-0000-0000-0000-000000000012', 'UNICO', 3),
    ('10000000-0000-0000-0000-000000000013', 'UNICO', 4),
    ('10000000-0000-0000-0000-000000000014', 'UNICO', 5),
    ('10000000-0000-0000-0000-000000000015', 'UNICO', 1)
ON CONFLICT (product_id, size) DO NOTHING;

INSERT INTO product_categories (product_id, category_id)
VALUES
    ('10000000-0000-0000-0000-000000000011', '30000000-0000-0000-0000-000000000007'), -- carteras
    ('10000000-0000-0000-0000-000000000012', '30000000-0000-0000-0000-000000000006'), -- zapatos
    ('10000000-0000-0000-0000-000000000012', '30000000-0000-0000-0000-000000000002'), -- invierno
    ('10000000-0000-0000-0000-000000000013', '30000000-0000-0000-0000-000000000004'), -- vestidos
    ('10000000-0000-0000-0000-000000000013', '30000000-0000-0000-0000-000000000003'), -- verano
    ('10000000-0000-0000-0000-000000000014', '30000000-0000-0000-0000-000000000008'), -- accesorios
    ('10000000-0000-0000-0000-000000000015', '30000000-0000-0000-0000-000000000002'), -- invierno
    ('10000000-0000-0000-0000-000000000015', '30000000-0000-0000-0000-000000000005')  -- pantalones
ON CONFLICT DO NOTHING;
