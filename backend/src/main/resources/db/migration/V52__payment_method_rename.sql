-- Rename old payment method values in orders table
UPDATE orders SET payment_method = CASE payment_method
    WHEN 'BANK_TRANSFER'       THEN 'TRANSFER'
    WHEN 'CASH_ON_DELIVERY'    THEN 'CASH'
    WHEN 'PAYMENT_GATEWAY'     THEN 'WEBPAY'
    WHEN 'AGREED_BY_WHATSAPP'  THEN 'OTHER'
    WHEN 'STORE_CREDIT'        THEN 'OTHER'
    ELSE payment_method
END
WHERE payment_method IN ('BANK_TRANSFER','CASH_ON_DELIVERY','PAYMENT_GATEWAY','AGREED_BY_WHATSAPP','STORE_CREDIT');

-- Rename in payments table
UPDATE payments SET method = CASE method
    WHEN 'BANK_TRANSFER'       THEN 'TRANSFER'
    WHEN 'CASH_ON_DELIVERY'    THEN 'CASH'
    WHEN 'PAYMENT_GATEWAY'     THEN 'WEBPAY'
    WHEN 'AGREED_BY_WHATSAPP'  THEN 'OTHER'
    WHEN 'STORE_CREDIT'        THEN 'OTHER'
    ELSE method
END
WHERE method IN ('BANK_TRANSFER','CASH_ON_DELIVERY','PAYMENT_GATEWAY','AGREED_BY_WHATSAPP','STORE_CREDIT');

-- Update the transfer-snapshot CHECK constraint to reference the new 'TRANSFER' value
ALTER TABLE payments
    DROP CONSTRAINT IF EXISTS chk_payments_transfer_snapshot_for_bank_transfer;

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_transfer_snapshot_for_bank_transfer
    CHECK (
        method <> 'TRANSFER'
        OR (
            transfer_account_holder_name IS NOT NULL
            AND LENGTH(TRIM(transfer_account_holder_name)) > 0
            AND transfer_account_email IS NOT NULL
            AND LENGTH(TRIM(transfer_account_email)) > 0
            AND transfer_account_number IS NOT NULL
            AND LENGTH(TRIM(transfer_account_number)) > 0
            AND transfer_bank_name IS NOT NULL
            AND LENGTH(TRIM(transfer_bank_name)) > 0
            AND transfer_account_type IS NOT NULL
            AND LENGTH(TRIM(transfer_account_type)) > 0
        )
    );

-- Drop the partial index that filtered on the old 'BANK_TRANSFER' value and recreate for 'TRANSFER'
DROP INDEX IF EXISTS idx_payments_pending_bank_transfer;

CREATE INDEX idx_payments_pending_transfer
    ON payments (method, status, created_at)
    WHERE status = 'PENDING' AND method = 'TRANSFER';
