ALTER TABLE system_settings
    ADD COLUMN IF NOT EXISTS n8n_webhook_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS n8n_api_key_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS n8n_token_header_name VARCHAR(120);

UPDATE system_settings
SET n8n_token_header_name = COALESCE(NULLIF(n8n_token_header_name, ''), 'X-PE-N8N-TOKEN')
WHERE id = 1;
