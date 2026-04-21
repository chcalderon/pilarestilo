ALTER TABLE system_settings
    ADD COLUMN IF NOT EXISTS notification_provider VARCHAR(40) NOT NULL DEFAULT 'LOG',
    ADD COLUMN IF NOT EXISTS whatsapp_simulated_to VARCHAR(40),
    ADD COLUMN IF NOT EXISTS whatsapp_simulated_sender VARCHAR(120),
    ADD COLUMN IF NOT EXISTS whatsapp_twilio_api_base_url VARCHAR(255),
    ADD COLUMN IF NOT EXISTS whatsapp_twilio_account_sid VARCHAR(255),
    ADD COLUMN IF NOT EXISTS whatsapp_twilio_auth_token_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS whatsapp_twilio_from VARCHAR(80),
    ADD COLUMN IF NOT EXISTS whatsapp_twilio_to_fallback VARCHAR(80),
    ADD COLUMN IF NOT EXISTS whatsapp_twilio_sender_alias VARCHAR(120),
    ADD COLUMN IF NOT EXISTS sendgrid_api_base_url VARCHAR(255),
    ADD COLUMN IF NOT EXISTS sendgrid_api_key_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS sendgrid_from_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS sendgrid_sender_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS sendgrid_to_fallback VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_system_settings_notification_provider'
    ) THEN
        ALTER TABLE system_settings
            ADD CONSTRAINT chk_system_settings_notification_provider
            CHECK (notification_provider IN ('LOG', 'WHATSAPP_SIMULATED', 'WHATSAPP_TWILIO', 'EMAIL_SENDGRID', 'EMAIL_SMTP'));
    END IF;
END $$;

UPDATE system_settings
SET notification_provider = CASE
        WHEN COALESCE(NULLIF(notification_provider, ''), 'LOG') <> 'LOG'
            THEN notification_provider
        WHEN smtp_host IS NOT NULL OR smtp_port IS NOT NULL OR smtp_from_email IS NOT NULL OR smtp_username IS NOT NULL
            THEN 'EMAIL_SMTP'
        ELSE 'LOG'
    END,
    whatsapp_simulated_to = COALESCE(NULLIF(whatsapp_simulated_to, ''), whatsapp_number),
    whatsapp_simulated_sender = COALESCE(NULLIF(whatsapp_simulated_sender, ''), 'Pilar Estilo'),
    whatsapp_twilio_api_base_url = COALESCE(NULLIF(whatsapp_twilio_api_base_url, ''), 'https://api.twilio.com'),
    whatsapp_twilio_to_fallback = COALESCE(NULLIF(whatsapp_twilio_to_fallback, ''), '+56900000000'),
    whatsapp_twilio_sender_alias = COALESCE(NULLIF(whatsapp_twilio_sender_alias, ''), 'Pilar Estilo'),
    sendgrid_api_base_url = COALESCE(NULLIF(sendgrid_api_base_url, ''), 'https://api.sendgrid.com'),
    sendgrid_sender_name = COALESCE(NULLIF(sendgrid_sender_name, ''), 'Pilar Estilo')
WHERE id = 1;
