-- Lets a customer change their mind, and keeps what they said before.
--
-- A review was one per product per user, enforced by uq_review_product_user, and there was no way
-- to edit one. So a single tap on the storefront's quick-rate control spent the customer's only
-- chance to review that product: rating recorded, and no way ever to say why, or to revise it
-- after wearing the thing twice.
--
-- Reviews become an append-only history. A new one supersedes the previous, and only the live row
-- counts towards the product's rating. The superseded rows stay: what somebody thought before is
-- a fact, and overwriting it would erase the reason a rating moved.
ALTER TABLE reviews
  ADD COLUMN superseded_at TIMESTAMPTZ;

COMMENT ON COLUMN reviews.superseded_at IS
  'Set when the same user reviews this product again. NULL is the live review; only NULL rows count towards ratings.';

-- The old constraint allowed exactly one row per product and user, which is what made a second
-- review impossible. The partial index keeps the rule that matters — one *live* review each —
-- while letting the superseded ones accumulate behind it.
ALTER TABLE reviews
  DROP CONSTRAINT uq_review_product_user;

CREATE UNIQUE INDEX uq_review_live_per_user
  ON reviews (product_id, user_id)
  WHERE superseded_at IS NULL;

-- Every existing row is somebody's current opinion, so they all stay live. No backfill needed;
-- the column defaults to NULL, which is precisely "this is the one that counts".

-- Ratings are read often and always exclude superseded rows.
CREATE INDEX idx_reviews_live_by_product
  ON reviews (product_id)
  WHERE superseded_at IS NULL;
