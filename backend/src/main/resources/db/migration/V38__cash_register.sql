CREATE TABLE cash_registers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id UUID NOT NULL REFERENCES users(id),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,
    opening_balance NUMERIC(12,2) NOT NULL,
    closing_balance NUMERIC(12,2),
    expected_balance NUMERIC(12,2),
    difference NUMERIC(12,2),
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    notes TEXT
);

CREATE TABLE cash_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cash_register_id UUID NOT NULL REFERENCES cash_registers(id),
    type VARCHAR(10) NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    description VARCHAR(255) NOT NULL,
    order_id UUID REFERENCES orders(id),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorded_by UUID NOT NULL REFERENCES users(id)
);

CREATE INDEX ON cash_registers(seller_id);
CREATE INDEX ON cash_registers(status);
CREATE INDEX ON cash_movements(cash_register_id);
