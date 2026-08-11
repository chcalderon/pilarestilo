-- Discount redemptions become reservable, and orders gain a human-quotable reference.
--
-- Until now Discount.apply() incremented times_used inside CreateOrderUseCase, i.e. when the
-- order was CREATED and payment had not happened. Nothing ever reversed it, so an abandoned
-- bank transfer -- which the auto-cancel job kills after bankTransferAutoCancelTimeoutMinutes --
-- burned the customer's code permanently.

-- ---------------------------------------------------------------------------------------------
-- 1. discount_code_usages becomes the authoritative redemption ledger
-- ---------------------------------------------------------------------------------------------

ALTER TABLE discount_code_usages
  ADD COLUMN order_id    UUID REFERENCES orders(id) ON DELETE SET NULL,
  ADD COLUMN status      VARCHAR(16) NOT NULL DEFAULT 'SETTLED',
  ADD COLUMN settled_at  TIMESTAMPTZ,
  ADD COLUMN released_at TIMESTAMPTZ;

COMMENT ON COLUMN discount_code_usages.used_at IS 'When the redemption was reserved (kept from V30).';
COMMENT ON COLUMN discount_code_usages.order_id IS 'Order that reserved it. NULL for rows predating V67.';

-- Historical rows are treated as consumed: their orders may well have been paid, and handing
-- capacity back to codes that were already spent is the one outcome we cannot undo.
UPDATE discount_code_usages SET settled_at = used_at WHERE settled_at IS NULL;

ALTER TABLE discount_code_usages ALTER COLUMN status DROP DEFAULT;

ALTER TABLE discount_code_usages
  ADD CONSTRAINT chk_dcu_status CHECK (status IN ('PENDING', 'SETTLED', 'RELEASED'));

-- The load-bearing change. uq_discount_user blocked a user from ever reusing a code, even one
-- released because their order was cancelled. A partial unique index keeps one-use-per-user
-- while a redemption is live and frees the code on release, without deleting the audit row.
ALTER TABLE discount_code_usages DROP CONSTRAINT uq_discount_user;

CREATE UNIQUE INDEX uq_dcu_active
  ON discount_code_usages (discount_id, user_id)
  WHERE status <> 'RELEASED';

-- Makes double-reserving one order impossible at the storage layer, so settle and release
-- always act on at most one row and stay idempotent.
CREATE UNIQUE INDEX uq_dcu_order
  ON discount_code_usages (order_id)
  WHERE order_id IS NOT NULL;

CREATE INDEX idx_dcu_pending
  ON discount_code_usages (status)
  WHERE status = 'PENDING';

-- ---------------------------------------------------------------------------------------------
-- 2. Provenance on the order
-- ---------------------------------------------------------------------------------------------

-- The ledger is enough to settle or release. These two columns exist because
-- DeleteDiscountUseCase hard-deletes and discount_code_usages.discount_id cascades: removing a
-- discount would erase every order's record of which code was applied. orders.discount_amount
-- has always been stored without that provenance, which is the defect being closed here.
-- Never read by the state machine.
ALTER TABLE orders
  ADD COLUMN discount_id   UUID REFERENCES discounts(id) ON DELETE SET NULL,
  ADD COLUMN discount_code VARCHAR(50);

COMMENT ON COLUMN orders.discount_code IS
  'Snapshot of the code applied. NULL for orders created before V67 -- the link was never stored and cannot be reconstructed.';

-- ---------------------------------------------------------------------------------------------
-- 3. Resync times_used against the ledger
-- ---------------------------------------------------------------------------------------------

-- GREATEST never lowers the counter. Codes consumed before V30 -- when this table did not exist
-- -- have times_used > 0 with no rows behind it; an exact resync would silently hand their
-- capacity back.
UPDATE discounts d
   SET times_used = GREATEST(
         d.times_used,
         COALESCE((SELECT COUNT(*)
                     FROM discount_code_usages u
                    WHERE u.discount_id = d.id
                      AND u.status <> 'RELEASED'), 0));

-- ---------------------------------------------------------------------------------------------
-- 4. Public order reference
-- ---------------------------------------------------------------------------------------------

-- 'PE-' + the first 10 uppercase hex chars of MD5(order id), e.g. PE-3F9A2C71B4.
--
-- Hex (0-9A-F) contains none of the pairs that get misheard over the phone or mistyped from a
-- bank statement: no O/0, no I/1/l, no S/5. Derived rather than sequential so order-service can
-- compute the same value from its own database when APP_ORDER_REMOTE_WRITE_ENABLED routes
-- creation there. MD5 is used as an identifier derivation, not as a security primitive.
ALTER TABLE orders ADD COLUMN public_reference VARCHAR(16);

UPDATE orders SET public_reference = 'PE-' || UPPER(SUBSTR(MD5(id::TEXT), 1, 10));

-- 40 bits over a realistic order count makes collisions unlikely but not impossible, and the
-- unique index below would fail the migration outright. Salt duplicates, oldest order keeps the
-- unsalted value -- the same rule CreateOrderUseCase applies when minting new references.
DO $$
DECLARE
  dup   RECORD;
  salt  INT;
  cand  TEXT;
BEGIN
  FOR dup IN
    SELECT id, public_reference
      FROM (SELECT id,
                   public_reference,
                   ROW_NUMBER() OVER (PARTITION BY public_reference
                                          ORDER BY created_at, id) AS rn
              FROM orders) ranked
     WHERE rn > 1
  LOOP
    salt := 1;
    LOOP
      cand := 'PE-' || UPPER(SUBSTR(MD5(dup.id::TEXT || '#' || salt), 1, 10));
      EXIT WHEN NOT EXISTS (SELECT 1 FROM orders WHERE public_reference = cand);
      salt := salt + 1;
    END LOOP;
    UPDATE orders SET public_reference = cand WHERE id = dup.id;
  END LOOP;
END $$;

-- Deliberately left nullable. NOT NULL is tightened in a later migration, once CreateOrderUseCase
-- mints the reference -- expand/contract, so this migration stays compatible with the currently
-- running application. Making it NOT NULL here fails every INSERT until the Java side ships.
-- Postgres unique indexes permit multiple NULLs, so uniqueness is already enforced for the rows
-- that have a value.
CREATE UNIQUE INDEX uq_orders_public_reference ON orders (public_reference);
