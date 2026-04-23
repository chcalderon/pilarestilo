ALTER TABLE system_settings
    DROP CONSTRAINT IF EXISTS chk_system_settings_notification_provider;

ALTER TABLE system_settings
    ADD CONSTRAINT chk_system_settings_notification_provider
        CHECK (notification_provider IN (
            'LOG',
            'WHATSAPP_SIMULATED',
            'WHATSAPP_TWILIO',
            'EMAIL_SENDGRID',
            'EMAIL_SMTP',
            'N8N_WEBHOOK'
        ));
