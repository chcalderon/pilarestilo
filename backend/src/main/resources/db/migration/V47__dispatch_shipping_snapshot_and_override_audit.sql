ALTER TABLE dispatches
    ADD COLUMN IF NOT EXISTS order_shipping_zone_code VARCHAR(24),
    ADD COLUMN IF NOT EXISTS order_shipping_courier_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS order_shipping_courier_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS order_shipping_address_reference TEXT,
    ADD COLUMN IF NOT EXISTS carrier_override_configured VARCHAR(160),
    ADD COLUMN IF NOT EXISTS carrier_override_selected VARCHAR(160),
    ADD COLUMN IF NOT EXISTS carrier_override_by UUID,
    ADD COLUMN IF NOT EXISTS carrier_override_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_dispatches_override_by
    ON dispatches(carrier_override_by);

UPDATE dispatches d
SET order_shipping_zone_code = o.shipping_zone_code,
    order_shipping_courier_id = o.shipping_courier_id,
    order_shipping_courier_name = o.shipping_courier_name,
    order_shipping_address_reference = o.shipping_address_reference
FROM orders o
WHERE d.order_id = o.id
  AND (
      d.order_shipping_zone_code IS NULL
      OR d.order_shipping_courier_id IS NULL
      OR d.order_shipping_courier_name IS NULL
      OR d.order_shipping_address_reference IS NULL
  );
