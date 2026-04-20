-- Denormalized rating fields on products (updated by ReviewSummaryListener)
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS avg_rating    DECIMAL(3,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS review_count  INT          NOT NULL DEFAULT 0;

-- Reviews
CREATE TABLE reviews (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID        NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    rating      SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title       VARCHAR(200),
    comment     TEXT,
    approved    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_review_product_user UNIQUE (product_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_reviews_product ON reviews (product_id);
CREATE INDEX IF NOT EXISTS idx_reviews_user    ON reviews (user_id);
