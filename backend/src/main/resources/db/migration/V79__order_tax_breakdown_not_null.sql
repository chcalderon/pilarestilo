-- Contract half of V76. The columns arrived nullable so the running backend could keep writing rows
-- while the new one rolled out; both codebases have been deployed writing them since, so the
-- nullability has no reason left to exist.
--
-- Same shape as V67 -> V68 for orders.public_reference.

-- Defensive, not decorative: a row could have been written by the old build in the window between
-- V76 running and the new container taking over. The formula is V76's, unchanged -- the net is
-- derived from the gross and the tax is the remainder, so net + tax = total holds exactly.
UPDATE orders
   SET tax_rate   = COALESCE(tax_rate, 19.00),
       net_amount = COALESCE(net_amount, ROUND(total_amount / 1.19)),
       tax_amount = COALESCE(tax_amount, total_amount - ROUND(total_amount / 1.19))
 WHERE net_amount IS NULL OR tax_amount IS NULL OR tax_rate IS NULL;

ALTER TABLE orders ALTER COLUMN net_amount SET NOT NULL;
ALTER TABLE orders ALTER COLUMN tax_amount SET NOT NULL;
ALTER TABLE orders ALTER COLUMN tax_rate   SET NOT NULL;

-- What the split is for, asserted rather than assumed. TaxBreakdown derives the tax by subtraction
-- precisely so this holds; a row that violates it was computed somewhere else.
ALTER TABLE orders
  ADD CONSTRAINT chk_orders_tax_reconciles
  CHECK (net_amount + tax_amount = total_amount);
