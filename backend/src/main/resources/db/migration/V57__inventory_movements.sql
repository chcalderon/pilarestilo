CREATE TABLE inventory_movements (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  product_id    UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
  variant_color VARCHAR(80),
  variant_size  VARCHAR(32),
  type          VARCHAR(20) NOT NULL,
  quantity      INT NOT NULL,
  reference_type VARCHAR(30),
  reference_id  UUID,
  recorded_by   UUID REFERENCES users(id),
  reason        TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_inv_mov_product_created ON inventory_movements(product_id, created_at DESC);
CREATE INDEX idx_inv_mov_reference ON inventory_movements(reference_id);
CREATE INDEX idx_inv_mov_type ON inventory_movements(type);
