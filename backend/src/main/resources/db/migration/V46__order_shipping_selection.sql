ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_zone_code VARCHAR(24),
    ADD COLUMN IF NOT EXISTS shipping_courier_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS shipping_courier_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS shipping_payment_mode VARCHAR(32),
    ADD COLUMN IF NOT EXISTS shipping_address_reference TEXT;
