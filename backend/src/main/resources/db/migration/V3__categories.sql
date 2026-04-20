-- Category taxonomy (single-level deep: parent = "Mujer", children = collections)

CREATE TABLE categories (
    id          UUID PRIMARY KEY,
    slug        VARCHAR(64) NOT NULL UNIQUE,
    name_es     VARCHAR(120) NOT NULL,
    name_en     VARCHAR(120) NOT NULL,
    parent_id   UUID NULL REFERENCES categories(id) ON DELETE SET NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    image_url   VARCHAR(500) NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_categories_parent ON categories(parent_id);
CREATE INDEX idx_categories_active ON categories(active);

CREATE TABLE product_categories (
    product_id  UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, category_id)
);

CREATE INDEX idx_pc_category ON product_categories(category_id);

-- Seed: women's category tree
INSERT INTO categories (id, slug, name_es, name_en, parent_id, sort_order, image_url) VALUES
  ('30000000-0000-0000-0000-000000000001', 'mujer',           'Mujer',           'Women',         NULL, 0,  'https://images.unsplash.com/photo-1529139574466-a303027c1d8b?w=900&q=80'),
  ('30000000-0000-0000-0000-000000000002', 'invierno',        'Invierno',        'Winter',        '30000000-0000-0000-0000-000000000001', 1, 'https://images.unsplash.com/photo-1544022613-e87ca75a784a?w=900&q=80'),
  ('30000000-0000-0000-0000-000000000003', 'verano',          'Verano',          'Summer',        '30000000-0000-0000-0000-000000000001', 2, 'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=900&q=80'),
  ('30000000-0000-0000-0000-000000000004', 'vestidos',        'Vestidos',        'Dresses',       '30000000-0000-0000-0000-000000000001', 3, 'https://images.unsplash.com/photo-1572804013309-59a88b7e92f1?w=900&q=80'),
  ('30000000-0000-0000-0000-000000000005', 'pantalones',      'Pantalones',      'Trousers',      '30000000-0000-0000-0000-000000000001', 4, 'https://images.unsplash.com/photo-1594633312681-425c7b97ccd1?w=900&q=80'),
  ('30000000-0000-0000-0000-000000000006', 'zapatos',         'Zapatos',         'Shoes',         '30000000-0000-0000-0000-000000000001', 5, 'https://images.unsplash.com/photo-1543163521-1bf539c55dd2?w=900&q=80'),
  ('30000000-0000-0000-0000-000000000007', 'carteras',        'Carteras',        'Bags',          '30000000-0000-0000-0000-000000000001', 6, 'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=900&q=80'),
  ('30000000-0000-0000-0000-000000000008', 'accesorios',      'Accesorios',      'Accessories',   '30000000-0000-0000-0000-000000000001', 7, 'https://images.unsplash.com/photo-1601924994987-69e26d50dc26?w=900&q=80');

-- Assign existing v1 seed products to categories (vestidos / carteras / accesorios / zapatos / pantalones / verano / invierno)
INSERT INTO product_categories (product_id, category_id) VALUES
  ('10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000004'), -- Vestido Midi Floral → vestidos
  ('10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000003'), -- → verano
  ('10000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000007'), -- Cartera LV → carteras
  ('10000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000002'), -- Blazer crema → invierno
  ('10000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000006'), -- Pumps → zapatos
  ('10000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000004'), -- Vestido cocktail → vestidos
  ('10000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000008'), -- Cinturón Gucci → accesorios
  ('10000000-0000-0000-0000-000000000007', '30000000-0000-0000-0000-000000000003'), -- Camisa seda → verano
  ('10000000-0000-0000-0000-000000000008', '30000000-0000-0000-0000-000000000004'), -- Maxifalda → vestidos
  ('10000000-0000-0000-0000-000000000008', '30000000-0000-0000-0000-000000000003'), -- → verano
  ('10000000-0000-0000-0000-000000000009', '30000000-0000-0000-0000-000000000007'), -- Speedy 30 → carteras
  ('10000000-0000-0000-0000-000000000010', '30000000-0000-0000-0000-000000000005'), -- Traje pantalón → pantalones
  ('10000000-0000-0000-0000-000000000010', '30000000-0000-0000-0000-000000000002'); -- → invierno
