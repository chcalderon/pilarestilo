ALTER TABLE system_settings
    ADD COLUMN IF NOT EXISTS bank_transfer_account_holder VARCHAR(160) NOT NULL DEFAULT 'Pilar Estilo',
    ADD COLUMN IF NOT EXISTS bank_transfer_contact_email VARCHAR(255) NOT NULL DEFAULT 'admin@pilarestilo.com',
    ADD COLUMN IF NOT EXISTS bank_transfer_account_number VARCHAR(120) NOT NULL DEFAULT '0000000000',
    ADD COLUMN IF NOT EXISTS bank_transfer_account_type VARCHAR(80) NOT NULL DEFAULT 'Cuenta Corriente';

UPDATE system_settings
SET bank_transfer_account_holder = COALESCE(NULLIF(TRIM(bank_transfer_account_holder), ''), 'Pilar Estilo'),
    bank_transfer_contact_email = COALESCE(NULLIF(TRIM(bank_transfer_contact_email), ''), 'admin@pilarestilo.com'),
    bank_transfer_account_number = COALESCE(NULLIF(TRIM(bank_transfer_account_number), ''), '0000000000'),
    bank_transfer_account_type = COALESCE(NULLIF(TRIM(bank_transfer_account_type), ''), 'Cuenta Corriente')
WHERE id = 1;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS transfer_account_holder_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS transfer_account_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS transfer_account_number VARCHAR(120),
    ADD COLUMN IF NOT EXISTS transfer_account_type VARCHAR(80);

UPDATE payments p
SET transfer_account_holder_name = COALESCE(NULLIF(TRIM(p.transfer_account_holder_name), ''), s.bank_transfer_account_holder),
    transfer_account_email = COALESCE(NULLIF(TRIM(p.transfer_account_email), ''), s.bank_transfer_contact_email),
    transfer_account_number = COALESCE(NULLIF(TRIM(p.transfer_account_number), ''), s.bank_transfer_account_number),
    transfer_account_type = COALESCE(NULLIF(TRIM(p.transfer_account_type), ''), s.bank_transfer_account_type)
FROM system_settings s
WHERE s.id = 1
  AND p.method = 'BANK_TRANSFER';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_payments_transfer_snapshot_for_bank_transfer'
    ) THEN
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
                    AND transfer_account_type IS NOT NULL
                    AND LENGTH(TRIM(transfer_account_type)) > 0
                )
            );
    END IF;
END $$;
