-- What a sale is worth, split the way the SII asks for it.
--
-- Orders stored subtotal, discount and total, all gross and none of them decomposed. A boleta needs
-- MntNeto, IVA and MntTotal, and the shop cannot report what it never recorded. Carrying the split
-- on the order rather than only on the document means a sale can be accounted for before its boleta
-- exists, and the numbers on the document are then a copy of a decision already made.
--
-- Expand half. The columns arrive nullable so the running backend keeps writing rows while the new
-- one rolls out; the SET NOT NULL contract follows in its own migration, after both codebases are
-- deployed. Same shape as V67 -> V68 for orders.public_reference.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS net_amount NUMERIC(15,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(15,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tax_rate   NUMERIC(5,2);

-- Chilean consumer prices are quoted VAT-inclusive, so total_amount is the gross figure and the net
-- is derived from it, never the other way round. The tax is then the remainder rather than a second
-- multiplication: that is what keeps net + tax = total exact to the peso. Computing both by
-- multiplying is how the discount rounding bug happened in this codebase.
UPDATE orders
   SET tax_rate   = 19.00,
       net_amount = ROUND(total_amount / 1.19),
       tax_amount = total_amount - ROUND(total_amount / 1.19)
 WHERE net_amount IS NULL;

COMMENT ON COLUMN orders.net_amount IS
  'Taxable base, derived from total_amount at the rate in force when the order was created.';
COMMENT ON COLUMN orders.tax_amount IS
  'total_amount - net_amount. Derived by subtraction so the three always reconcile exactly.';
COMMENT ON COLUMN orders.tax_rate IS
  'VAT percentage applied at creation time, snapshotted so a future rate change cannot restate past sales.';

-- Rate 19.00 is hardcoded here rather than read from system_settings on purpose: this backfill
-- describes orders that already happened under the rate that was in force, and V74 emptied the
-- table anyway, so in practice it updates nothing. New orders take the rate from settings.
