ALTER TABLE wishlists
    ADD COLUMN IF NOT EXISTS share_token UUID;

ALTER TABLE wishlists
    ADD COLUMN IF NOT EXISTS share_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS ux_wishlists_share_token
    ON wishlists (share_token)
    WHERE share_token IS NOT NULL;
