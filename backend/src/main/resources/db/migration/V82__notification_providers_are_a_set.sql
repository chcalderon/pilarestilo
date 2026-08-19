-- Notifying over one channel was never a decision, it was a limitation.
--
-- notification_provider held a single value and the sender switched on it to pick exactly one
-- adapter, so turning on WhatsApp silently stopped every email -- including the transfer
-- instructions and the written confirmation the Ley 21.398 requires. A shop notifies over the
-- channels it uses, which is usually more than one.
--
-- Same shape payment_gateway_providers already has: a comma-joined list in one column, normalised
-- by the domain. A rename rather than a new column, because there is one writer (the monolith) and
-- two columns holding the same setting is how they drift.

ALTER TABLE system_settings DROP CONSTRAINT IF EXISTS chk_system_settings_notification_provider;

ALTER TABLE system_settings RENAME COLUMN notification_provider TO notification_providers;

ALTER TABLE system_settings ALTER COLUMN notification_providers TYPE VARCHAR(255);
ALTER TABLE system_settings ALTER COLUMN notification_providers SET DEFAULT 'LOG';

-- A row with no channel at all would send nothing and say nothing about why.
UPDATE system_settings
   SET notification_providers = 'LOG'
 WHERE notification_providers IS NULL OR TRIM(notification_providers) = '';

ALTER TABLE system_settings ALTER COLUMN notification_providers SET NOT NULL;

ALTER TABLE system_settings
  ADD CONSTRAINT chk_system_settings_notification_providers
  CHECK (length(TRIM(BOTH FROM notification_providers)) > 0);

COMMENT ON COLUMN system_settings.notification_providers IS
  'Comma-joined NotificationProvider values, all of them active at once. Normalised by SystemSettings; unknown values are refused at the domain, not here, so adding a provider needs no migration.';
