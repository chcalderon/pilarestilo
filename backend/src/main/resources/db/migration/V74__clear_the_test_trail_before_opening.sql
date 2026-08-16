-- Clears the trail left by testing, before the shop opens to anybody.
--
-- The site is not live yet: every order, payment, dispatch and review in this database — and in
-- production — was created by testing. The addresses say so on their face (flow_*@test.com,
-- ui_smoke_*@test.com, the seed admin, and the owner's own account).
--
-- Left in place, the shop would open showing 55 orders it never received, 23 parcels waiting to be
-- packed, sales figures on its dashboard that mean nothing, and product ratings nobody gave. That
-- is worse than a repair, which is why V73 corrected the state of rows this now removes: that
-- migration is kept for the record of the defect it describes, not because the rows survive.
--
-- Deletes only the transactional trail. Products, prices, categories, navigation, users, roles,
-- permissions, system settings and discount definitions are untouched — they are the shop, not its
-- history.

-- Notifications reference orders and payments that are about to stop existing.
DELETE FROM notifications;

-- Cash movements from POS testing. The registers themselves are both CLOSED and stay, since the
-- register is a fixture of the shop rather than a record of a sale.
DELETE FROM cash_movements;
DELETE FROM credit_movements;

-- Reviews of products nobody bought. Nothing here is a customer's opinion.
DELETE FROM reviews;

-- The stock ledger describes movements caused by the orders below.
DELETE FROM inventory_movements;

-- Redemptions are freed before the discounts they point at are counted again.
DELETE FROM discount_code_usages;

-- Children of orders first. Every one of these foreign keys is ON DELETE NO ACTION — none of them
-- cascades, which is worth stating because assuming otherwise is what made the first version of
-- this migration fail: order_items was left out and Postgres refused to delete a single order.
DELETE FROM dispatches;
DELETE FROM payments;
DELETE FROM order_items;
DELETE FROM orders;

-- Everything below is derived from what was just deleted, so these are corrections, not guesses.

-- No orders means no reservations. Anything still marked reserved was held for a row that no longer
-- exists, and would otherwise make stock look unavailable forever.
UPDATE product_variants
   SET stock_reserved = 0
 WHERE stock_reserved <> 0;

-- times_used counts active redemptions, and there are none left.
UPDATE discounts
   SET times_used = 0
 WHERE times_used <> 0;

-- No reviews means no rating.
UPDATE products
   SET avg_rating = 0,
       review_count = 0
 WHERE avg_rating <> 0
    OR review_count <> 0;

-- products.stock and product_variants.stock_on_hand are deliberately NOT reset.
--
-- Testing moved them — one product sits at zero because test orders drained it — and the real
-- counts are not derivable from anything in this database. Only the shop knows what is on its
-- shelves. Setting a number here would look like a repair and be an invention; the counts have to
-- be entered from the admin before opening.
