-- Moves orders to where their payment already said they were.
--
-- Until 2026-08-16 several listeners were in-process only, and KafkaDomainEventPublisher is
-- @Primary, so with Kafka on — production — they never ran. A payment was approved or rejected and
-- the order stayed where it was. Locally that left 20 orders behind: 9 paid for and still CREATED,
-- 10 rejected and never cancelled, 1 with a receipt uploaded and never marked under review.
--
-- The listeners are fixed; this is only the residue. Every statement is idempotent and scoped by
-- the mismatch it repairs, so running it against a database with nothing to fix changes nothing.
--
-- Deliberately silent: plain SQL fires no domain event, so no customer receives a message about an
-- order from months ago. The state is corrected; the conversation is not reopened.

-- 1. A rejected payment means a cancelled order.
UPDATE orders o
   SET status = 'CANCELLED',
       updated_at = now()
  FROM payments p
 WHERE p.order_id = o.id
   AND p.status = 'REJECTED'
   AND o.status = 'CREATED';

-- 2. A receipt uploaded and never judged belongs in the reviewer's queue.
UPDATE orders o
   SET status = 'PAYMENT_UNDER_REVIEW',
       updated_at = now()
  FROM payments p
 WHERE p.order_id = o.id
   AND p.status = 'SUBMITTED'
   AND o.status = 'CREATED';

-- 3. An approved payment means a paid order.
UPDATE orders o
   SET status = 'PAID',
       updated_at = now()
  FROM payments p
 WHERE p.order_id = o.id
   AND p.status = 'APPROVED'
   AND o.status = 'CREATED';

-- 4. A paid order needs somewhere to be packed from.
--
-- Step 3 corrects the record but fires no event, so OrderPaidDispatchListener never sees these and
-- they would sit paid with nobody able to pack them — which is how they were invisible in the first
-- place. The shipping snapshot is copied from the order, the way the listener does it.
INSERT INTO dispatches (order_id, status, order_shipping_zone_code, order_shipping_courier_id,
                        order_shipping_courier_name, order_shipping_address_reference, created_at)
SELECT o.id,
       'PENDING',
       o.shipping_zone_code,
       o.shipping_courier_id,
       o.shipping_courier_name,
       o.shipping_address_reference,
       now()
  FROM orders o
  JOIN payments p ON p.order_id = o.id
 WHERE p.status = 'APPROVED'
   AND o.status = 'PAID'
   AND NOT EXISTS (SELECT 1 FROM dispatches d WHERE d.order_id = o.id);

-- Stock is left alone, on purpose.
--
-- None of these orders is holding a reservation — stock_reserved is 0 for every product they name.
-- They predate the reserved-stock model, so their items carry no colour or size and they went
-- through the legacy aggregate path, which decrements products.stock at reservation time. Those
-- units left months ago and the counts have been adjusted by hand since. Adding them back now would
-- not be a repair, it would be a guess laid on top of whatever the real count has become.
