CREATE TABLE IF NOT EXISTS customer_addresses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label VARCHAR(80) NOT NULL,
    recipient_name VARCHAR(180) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    line1 VARCHAR(255) NOT NULL,
    line2 VARCHAR(255),
    comuna VARCHAR(140) NOT NULL,
    city VARCHAR(140) NOT NULL,
    region VARCHAR(140) NOT NULL,
    reference TEXT,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_customer_addresses_customer_updated
    ON customer_addresses(customer_id, updated_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_addresses_default_per_customer
    ON customer_addresses(customer_id)
    WHERE is_default = TRUE;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_address_id UUID;

