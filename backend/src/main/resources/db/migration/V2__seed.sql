-- Seed: users
INSERT INTO users (id, email, full_name, role, password_hash) VALUES
  ('00000000-0000-0000-0000-000000000001', 'admin@pilarestilo.com', 'Pilar Admin', 'ADMIN', '$2a$12$placeholder_admin_hash'),
  ('00000000-0000-0000-0000-000000000002', 'vendedor@pilarestilo.com', 'Sofia Vendedora', 'SELLER', '$2a$12$placeholder_seller_hash'),
  ('00000000-0000-0000-0000-000000000003', 'cliente1@example.com', 'Valentina Rodriguez', 'CUSTOMER', NULL),
  ('00000000-0000-0000-0000-000000000004', 'cliente2@example.com', 'Camila Fernandez', 'CUSTOMER', NULL);

-- Seed: products (10 luxury items, mix NEW/USED) — images from Unsplash
INSERT INTO products (id, name, description, price_amount, price_currency, image_url, condition, brand, stock, active) VALUES
  ('10000000-0000-0000-0000-000000000001',
   'Vestido Midi Floral',
   'Elegante vestido midi con estampado floral, perfecto para ocasiones especiales. Talla M.',
   285000.00, 'ARS',
   'https://images.unsplash.com/photo-1572804013309-59a88b7e92f1?w=600&q=80',
   'NEW', 'Zara Mujer', 3, true),

  ('10000000-0000-0000-0000-000000000002',
   'Cartera Monogram',
   'Icónica cartera Louis Vuitton Monogram Canvas en excelente estado. Incluye dustbag original.',
   480000.00, 'ARS',
   'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=600&q=80',
   'USED', 'Louis Vuitton', 1, true),

  ('10000000-0000-0000-0000-000000000003',
   'Blazer Clásico Crema',
   'Blazer estructurado en color crema, corte italiano. Ideal para looks de oficina con toque de lujo.',
   175000.00, 'ARS',
   'https://images.unsplash.com/photo-1594938298603-c8148c4b4f6f?w=600&q=80',
   'NEW', 'Massimo Dutti', 5, true),

  ('10000000-0000-0000-0000-000000000004',
   'Pumps Stiletto Nude',
   'Zapatos stiletto en cuero genuino color nude, taco 10cm. Talla 37. Usados dos veces.',
   120000.00, 'ARS',
   'https://images.unsplash.com/photo-1543163521-1bf539c55dd2?w=600&q=80',
   'USED', 'Valentino', 1, true),

  ('10000000-0000-0000-0000-000000000005',
   'Vestido Cocktail Rojo',
   'Vestido de cóctel en rojo intenso, espalda cruzada. Colección exclusiva. Talla S.',
   320000.00, 'ARS',
   'https://images.unsplash.com/photo-1515372039744-b8f02a3ae446?w=600&q=80',
   'NEW', 'Carolina Herrera', 2, true),

  ('10000000-0000-0000-0000-000000000006',
   'Cinturón Gucci GG Marmont',
   'Cinturón Gucci GG Marmont en cuero negro, hebilla dorada. Talla 85. Estado impecable.',
   195000.00, 'ARS',
   'https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=600&q=80',
   'USED', 'Gucci', 1, true),

  ('10000000-0000-0000-0000-000000000007',
   'Camisa de Seda Off-White',
   'Camisa de seda pura en tono off-white, manga larga con botones nacarados. Talla 38.',
   98000.00, 'ARS',
   'https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=600&q=80',
   'NEW', 'Massimo Dutti', 4, true),

  ('10000000-0000-0000-0000-000000000008',
   'Maxifalda Plisada',
   'Maxifalda plisada en gasa de seda, colores pastel degradados. Talla única.',
   145000.00, 'ARS',
   'https://images.unsplash.com/photo-1583496661160-fb5218ees893?w=600&q=80',
   'NEW', 'Zara Mujer', 3, true),

  ('10000000-0000-0000-0000-000000000009',
   'Sac de Poing Speedy 30',
   'Louis Vuitton Speedy 30 Damier Ebène. Viene con candado, llaves y dustbag. Estado muy bueno.',
   500000.00, 'ARS',
   'https://images.unsplash.com/photo-1566150905458-1bf1fc113f0d?w=600&q=80',
   'USED', 'Louis Vuitton', 1, true),

  ('10000000-0000-0000-0000-000000000010',
   'Traje Pantalón Marino',
   'Traje pantalón en lana fría marino, chaqueta slim fit con pantalón de tiro alto. Talla 40.',
   380000.00, 'ARS',
   'https://images.unsplash.com/photo-1591369822096-ffd140ec948f?w=600&q=80',
   'NEW', 'Carolina Herrera', 2, true);

-- Seed: discount code BIENVENIDA10
INSERT INTO discounts (id, code, type, value, min_order_amount, min_order_currency, valid_from, valid_until, max_uses, times_used, active) VALUES
  ('20000000-0000-0000-0000-000000000001',
   'BIENVENIDA10',
   'PERCENTAGE',
   10.00,
   100000.00,
   'ARS',
   '2025-01-01',
   '2027-12-31',
   100,
   0,
   true);
