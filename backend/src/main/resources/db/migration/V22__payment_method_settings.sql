ALTER TABLE system_settings
    ADD COLUMN IF NOT EXISTS payment_method_bank_transfer_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS payment_method_gateway_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS payment_gateway_providers VARCHAR(255) NOT NULL DEFAULT 'MERCADO_PAGO';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_system_settings_payment_method_any_enabled'
    ) THEN
        ALTER TABLE system_settings
            ADD CONSTRAINT chk_system_settings_payment_method_any_enabled
            CHECK (payment_method_bank_transfer_enabled OR payment_method_gateway_enabled);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_system_settings_payment_gateway_providers_when_enabled'
    ) THEN
        ALTER TABLE system_settings
            ADD CONSTRAINT chk_system_settings_payment_gateway_providers_when_enabled
            CHECK (
                NOT payment_method_gateway_enabled
                OR (payment_gateway_providers IS NOT NULL AND LENGTH(TRIM(payment_gateway_providers)) > 0)
            );
    END IF;
END $$;

UPDATE system_settings
SET payment_method_bank_transfer_enabled = COALESCE(payment_method_bank_transfer_enabled, TRUE),
    payment_method_gateway_enabled = COALESCE(payment_method_gateway_enabled, TRUE),
    payment_gateway_providers = COALESCE(NULLIF(TRIM(payment_gateway_providers), ''), 'MERCADO_PAGO')
WHERE id = 1;
