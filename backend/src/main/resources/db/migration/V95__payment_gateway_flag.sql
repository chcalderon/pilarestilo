-- A gateway reversal after the money already moved (refund, chargeback) used to throw inside the
-- webhook handler -- the gateway's status string ("refunded") matched no case, Mercado Pago
-- retried a few times and gave up, and the order sat forever showing paid with no boleta while
-- the money was already back on the customer's card. This column is where that reversal now
-- lands instead: a payment stays whatever status it already reached (approved payments never get
-- silently flipped by a webhook -- an admin looks and cancels the order, same as any other undone
-- sale), and the flag is what tells the admin to look.

ALTER TABLE payments
    ADD COLUMN gateway_flag VARCHAR(32),
    ADD COLUMN gateway_flagged_at TIMESTAMPTZ;
