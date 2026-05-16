ALTER TABLE orders ADD COLUMN IF NOT EXISTS sales_channel VARCHAR(20) NOT NULL DEFAULT 'ECOMMERCE';

UPDATE orders SET sales_channel = 'ECOMMERCE' WHERE sales_channel IS NULL OR sales_channel = '';

CREATE INDEX IF NOT EXISTS idx_orders_sales_channel ON orders(sales_channel);
