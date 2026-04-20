-- Serve product images from backend-managed media paths instead of external URLs.
-- These paths are resolved by backend static mapping: /api/media/** -> MEDIA_STORAGE_PATH.

UPDATE products
SET image_url = '/api/media/products/product-001.jpg'
WHERE id = '10000000-0000-0000-0000-000000000001';

UPDATE products
SET image_url = '/api/media/products/product-002.jpg'
WHERE id = '10000000-0000-0000-0000-000000000002';

UPDATE products
SET image_url = '/api/media/products/product-003.jpg'
WHERE id = '10000000-0000-0000-0000-000000000003';

UPDATE products
SET image_url = '/api/media/products/product-004.jpg'
WHERE id = '10000000-0000-0000-0000-000000000004';

UPDATE products
SET image_url = '/api/media/products/product-005.jpg'
WHERE id = '10000000-0000-0000-0000-000000000005';

UPDATE products
SET image_url = '/api/media/products/product-006.jpg'
WHERE id = '10000000-0000-0000-0000-000000000006';

UPDATE products
SET image_url = '/api/media/products/product-007.jpg'
WHERE id = '10000000-0000-0000-0000-000000000007';

UPDATE products
SET image_url = '/api/media/products/product-008.jpg'
WHERE id = '10000000-0000-0000-0000-000000000008';

UPDATE products
SET image_url = '/api/media/products/product-009.jpg'
WHERE id = '10000000-0000-0000-0000-000000000009';

UPDATE products
SET image_url = '/api/media/products/product-010.jpg'
WHERE id = '10000000-0000-0000-0000-000000000010';
