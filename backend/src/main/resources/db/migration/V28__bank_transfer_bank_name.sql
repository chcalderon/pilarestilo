ALTER TABLE system_settings
    ADD COLUMN IF NOT EXISTS bank_transfer_bank_name VARCHAR(120) NOT NULL DEFAULT 'Banco de Chile';

UPDATE system_settings
SET bank_transfer_bank_name = COALESCE(NULLIF(TRIM(bank_transfer_bank_name), ''), 'Banco de Chile')
WHERE id = 1;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS transfer_bank_name VARCHAR(120);

UPDATE payments p
SET transfer_bank_name = COALESCE(NULLIF(TRIM(p.transfer_bank_name), ''), s.bank_transfer_bank_name)
FROM system_settings s
WHERE s.id = 1
  AND p.method = 'BANK_TRANSFER';

ALTER TABLE payments
    DROP CONSTRAINT IF EXISTS chk_payments_transfer_snapshot_for_bank_transfer;

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_transfer_snapshot_for_bank_transfer
    CHECK (
        method <> 'BANK_TRANSFER'
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
