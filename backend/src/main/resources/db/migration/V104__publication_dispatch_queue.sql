-- Publication dispatch outbox: a single "ready to be worked" timestamp + a retry state.
ALTER TABLE publications ADD COLUMN next_attempt_at TIMESTAMPTZ;

-- The worker's hot query: rows waiting to be dispatched whose time has come.
CREATE INDEX idx_publications_dispatch_due
    ON publications (next_attempt_at)
    WHERE status IN ('APPROVED', 'SCHEDULED', 'RETRY_SCHEDULED');

-- Any row currently mid-flight gets a deterministic pickup time instead of NULL-and-ignored.
UPDATE publications
   SET next_attempt_at = COALESCE(scheduled_at, now())
 WHERE status IN ('APPROVED', 'SCHEDULED');
