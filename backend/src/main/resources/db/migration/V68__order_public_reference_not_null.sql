-- Contract half of the expand/contract pair started in V67.
--
-- V67 added orders.public_reference nullable and backfilled it, deliberately leaving the
-- constraint off: the application running at that moment did not know the column, and NOT NULL
-- would have rejected every INSERT until the Java side shipped. CreateOrderUseCase now mints the
-- reference through OrderReference, so the column can be made mandatory.
--
-- Any row still NULL predates that code and would have been created between the two deploys.
-- Backfilled with the same expression V67 used, so a reference is never left blank.
UPDATE orders
   SET public_reference = 'PE-' || UPPER(SUBSTR(MD5(id::TEXT), 1, 10))
 WHERE public_reference IS NULL;

-- Duplicates cannot survive the unique index V67 already created, so no repair loop is needed
-- here: uq_orders_public_reference would have rejected the UPDATE above on collision.
ALTER TABLE orders ALTER COLUMN public_reference SET NOT NULL;
