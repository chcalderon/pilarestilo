CREATE TABLE publication_batches (
    id               UUID PRIMARY KEY,
    caption_template TEXT        NOT NULL,
    hashtags_json    TEXT        NOT NULL,
    campaign_label   VARCHAR(120),
    created_by       UUID,
    created_at       TIMESTAMPTZ NOT NULL
);

ALTER TABLE publications ADD COLUMN batch_id UUID REFERENCES publication_batches(id);
ALTER TABLE publications ADD COLUMN external_permalink TEXT;

CREATE INDEX idx_publications_batch_id ON publications(batch_id);
CREATE INDEX idx_publication_batches_created_at ON publication_batches(created_at DESC);
