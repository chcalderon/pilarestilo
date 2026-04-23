ALTER TABLE system_settings
    ADD COLUMN IF NOT EXISTS media_storage_provider VARCHAR(40) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN IF NOT EXISTS media_s3_endpoint VARCHAR(255),
    ADD COLUMN IF NOT EXISTS media_s3_region VARCHAR(120),
    ADD COLUMN IF NOT EXISTS media_s3_bucket VARCHAR(120),
    ADD COLUMN IF NOT EXISTS media_s3_access_key_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS media_s3_secret_key_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS media_s3_path_style_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS media_s3_public_base_url VARCHAR(500);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_system_settings_media_storage_provider'
    ) THEN
        ALTER TABLE system_settings
            ADD CONSTRAINT chk_system_settings_media_storage_provider
            CHECK (media_storage_provider IN ('LOCAL', 'S3_COMPATIBLE'));
    END IF;
END $$;

UPDATE system_settings
SET media_storage_provider = COALESCE(NULLIF(media_storage_provider, ''), 'LOCAL'),
    media_s3_path_style_enabled = COALESCE(media_s3_path_style_enabled, FALSE)
WHERE id = 1;
