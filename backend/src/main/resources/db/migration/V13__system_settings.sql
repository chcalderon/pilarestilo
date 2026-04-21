CREATE TABLE IF NOT EXISTS system_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    whatsapp_number VARCHAR(40) NOT NULL,
    instagram_url VARCHAR(500),
    facebook_url VARCHAR(500),
    smtp_host VARCHAR(255),
    smtp_port INTEGER,
    smtp_username VARCHAR(255),
    smtp_from_email VARCHAR(255),
    smtp_password_encrypted TEXT,
    smtp_auth_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    smtp_starttls_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(120)
);

INSERT INTO system_settings (
    id,
    whatsapp_number,
    instagram_url,
    facebook_url,
    smtp_auth_enabled,
    smtp_starttls_enabled,
    updated_at,
    updated_by
) VALUES (
    1,
    '+56900000000',
    'https://instagram.com/pilarestilo',
    'https://facebook.com/pilarestilo',
    TRUE,
    TRUE,
    NOW(),
    'system-seed'
)
ON CONFLICT (id) DO NOTHING;
