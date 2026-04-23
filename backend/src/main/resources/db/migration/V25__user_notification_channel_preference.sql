ALTER TABLE users
    ADD COLUMN IF NOT EXISTS notification_channel_preference VARCHAR(20) NOT NULL DEFAULT 'AUTO';

UPDATE users
SET notification_channel_preference = COALESCE(NULLIF(notification_channel_preference, ''), 'AUTO');
