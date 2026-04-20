-- ─── Update seed user passwords with real BCrypt hashes ────────────────────
-- admin@pilarestilo.com   → admin2026
-- vendedor@pilarestilo.com → sofia2026
-- cliente1@example.com   → valentina2026
-- cliente2@example.com   → camila2026
UPDATE users SET password_hash = '$2a$10$XYil9LVbQs4XvZw2z63SQuy8Gl2v2rU/.BQqJliEc2KX/pIyXqUGW'
  WHERE id = '00000000-0000-0000-0000-000000000001';
UPDATE users SET password_hash = '$2a$10$IZTKwbuO13yG7iXfV6ccfekAf9GsAvV5.E3YtOk8KwSe6Vcq5GNdy'
  WHERE id = '00000000-0000-0000-0000-000000000002';
UPDATE users SET password_hash = '$2a$10$iS1WkKCnjoRdWaCh4Q3Q1uotJ1EFP4wUmTLppk783gM72KdbFLSz6'
  WHERE id = '00000000-0000-0000-0000-000000000003';
UPDATE users SET password_hash = '$2a$10$BhkKiEwkwhlaPrFa8Tqgd.gkUxX1oDcT4vbc72XMtI6V7OL.98DPO'
  WHERE id = '00000000-0000-0000-0000-000000000004';

-- ─── Sample reviews (approved = true, ratings 3-5) ──────────────────────────
INSERT INTO reviews (id, product_id, user_id, rating, title, comment, approved, created_at) VALUES
  -- Vestido Midi Floral (prod 1)
  ('30000000-0000-0000-0000-000000000001',
   '10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003',
   4, 'Hermoso vestido', 'La tela es de muy buena calidad y el corte es ideal. Lo recomiendo.', true, NOW()),
  ('30000000-0000-0000-0000-000000000002',
   '10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000004',
   5, 'Perfecto para una cena', 'Llegó en perfecto estado. La calidad supera el precio. Volveré a comprar.', true, NOW()),

  -- Cartera Monogram (prod 2)
  ('30000000-0000-0000-0000-000000000003',
   '10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002',
   5, 'Impecable estado', 'La cartera es exactamente como se describe. El dustbag original incluido.', true, NOW()),
  ('30000000-0000-0000-0000-000000000004',
   '10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003',
   4, 'Muy buena compra', 'Me encantó. Algunas marcas mínimas de uso que no se notan al usarla.', true, NOW()),

  -- Blazer Crema (prod 3)
  ('30000000-0000-0000-0000-000000000005',
   '10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000004',
   4, 'Corte elegante', 'El blazer es hermoso. La tela no se arruga fácilmente. Talle justo.', true, NOW()),

  -- Pumps Stiletto (prod 4)
  ('30000000-0000-0000-0000-000000000006',
   '10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000003',
   3, 'Bonitos pero incómodos', 'Son preciosos visualmente pero para caminar mucho no son los mejores.', true, NOW()),

  -- Vestido Cocktail Rojo (prod 5)
  ('30000000-0000-0000-0000-000000000007',
   '10000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000002',
   5, 'Divino para eventos', 'Lo usé en una boda y recibí mil halagos. La tela cae perfecto.', true, NOW()),
  ('30000000-0000-0000-0000-000000000008',
   '10000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000004',
   4, 'Muy lindo', 'El color es muy llamativo en persona, más vibrante que en la foto.', true, NOW()),

  -- Cinturón Gucci (prod 6)
  ('30000000-0000-0000-0000-000000000009',
   '10000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000003',
   4, 'Clásico atemporal', 'El estado es impecable como dice la descripción. La hebilla brilla perfecta.', true, NOW()),

  -- Camisa Seda (prod 7)
  ('30000000-0000-0000-0000-000000000010',
   '10000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000002',
   4, 'Seda de calidad', 'La camisa es suave y cae muy bien. Los botones nacarados le dan un toque especial.', true, NOW()),

  -- Speedy 30 (prod 9)
  ('30000000-0000-0000-0000-000000000011',
   '10000000-0000-0000-0000-000000000009', '00000000-0000-0000-0000-000000000004',
   5, 'La compra de mi vida', 'Bolso auténtico, muy bien conservado. El vendedor fue muy prolijo con el embalaje.', true, NOW()),

  -- Traje Pantalón (prod 10)
  ('30000000-0000-0000-0000-000000000012',
   '10000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000003',
   5, 'Elegancia total', 'El traje es impecable. La lana no pica y el corte slim es muy favorecedor.', true, NOW()),
  ('30000000-0000-0000-0000-000000000013',
   '10000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000002',
   4, 'Muy buen diseño', 'El corte moderno y la calidad de la tela son excelentes. Lo recomiendo.', true, NOW());

-- ─── Update denormalized rating summary on products ─────────────────────────
UPDATE products SET avg_rating = 4.50, review_count = 2 WHERE id = '10000000-0000-0000-0000-000000000001';
UPDATE products SET avg_rating = 4.50, review_count = 2 WHERE id = '10000000-0000-0000-0000-000000000002';
UPDATE products SET avg_rating = 4.00, review_count = 1 WHERE id = '10000000-0000-0000-0000-000000000003';
UPDATE products SET avg_rating = 3.00, review_count = 1 WHERE id = '10000000-0000-0000-0000-000000000004';
UPDATE products SET avg_rating = 4.50, review_count = 2 WHERE id = '10000000-0000-0000-0000-000000000005';
UPDATE products SET avg_rating = 4.00, review_count = 1 WHERE id = '10000000-0000-0000-0000-000000000006';
UPDATE products SET avg_rating = 4.00, review_count = 1 WHERE id = '10000000-0000-0000-0000-000000000007';
UPDATE products SET avg_rating = 5.00, review_count = 1 WHERE id = '10000000-0000-0000-0000-000000000009';
UPDATE products SET avg_rating = 4.50, review_count = 2 WHERE id = '10000000-0000-0000-0000-000000000010';
