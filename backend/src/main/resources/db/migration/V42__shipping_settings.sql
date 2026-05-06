-- Add admin-configurable shipping settings to system_settings.
-- shipping_zones_json: JSON array of zone configs (code, titleEs/En, etaEs/En, comunas[], active, sortOrder)
-- shipping_couriers_json: JSON array of courier configs (id, name, logoUrl, active)
-- shipping_payment_mode: enum-like VARCHAR (POR_PAGAR is the only value for now)
ALTER TABLE system_settings
    ADD COLUMN IF NOT EXISTS shipping_zones_json TEXT,
    ADD COLUMN IF NOT EXISTS shipping_couriers_json TEXT,
    ADD COLUMN IF NOT EXISTS shipping_payment_mode VARCHAR(32);

-- Seed defaults: zones reflect Los Andes-based store, couriers Starken + ChilExpress, payment por pagar.
UPDATE system_settings
SET
    shipping_zones_json = COALESCE(NULLIF(shipping_zones_json, ''), $$[
      {"code":"LOCAL","titleEs":"Zona local","titleEn":"Local zone","etaEs":"24-48 hs","etaEn":"24-48h","comunas":["Los Andes","San Felipe","Calle Larga","Rinconada"],"active":true,"sortOrder":1},
      {"code":"REGIONAL","titleEs":"V Region y RM","titleEn":"Valparaiso Region and Metropolitan Region","etaEs":"2-4 dias habiles","etaEn":"2-4 business days","comunas":[],"active":true,"sortOrder":2},
      {"code":"NACIONAL","titleEs":"Otras regiones","titleEn":"Other Chilean regions","etaEs":"3-7 dias habiles","etaEn":"3-7 business days","comunas":[],"active":true,"sortOrder":3}
    ]$$),
    shipping_couriers_json = COALESCE(NULLIF(shipping_couriers_json, ''), $$[
      {"id":"starken","name":"Starken","logoUrl":null,"active":true},
      {"id":"chilexpress","name":"ChilExpress","logoUrl":null,"active":true}
    ]$$),
    shipping_payment_mode = COALESCE(NULLIF(shipping_payment_mode, ''), 'POR_PAGAR')
WHERE id = 1;
