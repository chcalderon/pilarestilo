ALTER TABLE publication_batches ADD COLUMN scheduled_at TIMESTAMPTZ;

CREATE INDEX idx_publications_scheduled_due
    ON publications (scheduled_at)
    WHERE status = 'SCHEDULED';
