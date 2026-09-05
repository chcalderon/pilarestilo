-- Instagram/Facebook posting credentials for Increment H. Ids are not secret (plain columns,
-- mirrors n8n_webhook_url); access tokens are (encrypted columns, mirrors n8n_api_key_encrypted).
ALTER TABLE system_settings
    ADD COLUMN IF NOT EXISTS meta_instagram_user_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS meta_instagram_access_token_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS meta_facebook_page_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS meta_facebook_page_access_token_encrypted TEXT;
