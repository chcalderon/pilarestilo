-- Tracks which user has used which discount code (one use per user per code)
CREATE TABLE discount_code_usages (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  discount_id   UUID NOT NULL REFERENCES discounts(id) ON DELETE CASCADE,
  user_id       UUID NOT NULL REFERENCES users(id)     ON DELETE CASCADE,
  used_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_discount_user UNIQUE (discount_id, user_id)
);

CREATE INDEX idx_dcu_discount_id ON discount_code_usages(discount_id);
CREATE INDEX idx_dcu_user_id     ON discount_code_usages(user_id);
