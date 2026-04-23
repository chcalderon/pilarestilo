ALTER TABLE system_settings
    ADD COLUMN IF NOT EXISTS payment_gateway_mp_api_base_url VARCHAR(255) NOT NULL DEFAULT 'https://api.mercadopago.com',
    ADD COLUMN IF NOT EXISTS payment_gateway_mp_access_token_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS payment_gateway_mp_success_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS payment_gateway_mp_pending_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS payment_gateway_mp_failure_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS payment_gateway_mp_notification_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS payment_gateway_mp_webhook_token_encrypted TEXT;

UPDATE system_settings
SET payment_gateway_mp_api_base_url = COALESCE(
        NULLIF(TRIM(payment_gateway_mp_api_base_url), ''),
        'https://api.mercadopago.com'
    )
WHERE id = 1;
