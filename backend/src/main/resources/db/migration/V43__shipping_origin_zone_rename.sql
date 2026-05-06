-- Rename product shipping_origin_zone codes to match new semantic enum:
--   SANTIAGO  -> LOCAL     (Los Andes + neighbouring towns)
--   RM        -> REGIONAL  (V Region + Region Metropolitana)
--   REGIONES  -> NACIONAL  (rest of Chile)
-- No CHECK constraint exists on the column, so a plain UPDATE + DEFAULT change is enough.

UPDATE products
SET shipping_origin_zone = CASE shipping_origin_zone
    WHEN 'SANTIAGO' THEN 'LOCAL'
    WHEN 'RM'       THEN 'REGIONAL'
    WHEN 'REGIONES' THEN 'NACIONAL'
    ELSE shipping_origin_zone
END
WHERE shipping_origin_zone IN ('SANTIAGO','RM','REGIONES');

ALTER TABLE products
    ALTER COLUMN shipping_origin_zone SET DEFAULT 'LOCAL';
