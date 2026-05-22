CREATE TABLE IF NOT EXISTS publications (
    id UUID PRIMARY KEY,
    product_id UUID NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id UUID NULL,
    platform VARCHAR(32) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    approval_status VARCHAR(32) NOT NULL,
    caption TEXT NULL,
    hashtags_json TEXT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'es-CL',
    campaign_label VARCHAR(120) NULL,
    scheduled_at TIMESTAMPTZ NULL,
    published_at TIMESTAMPTZ NULL,
    external_post_id VARCHAR(255) NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    content_version INTEGER NOT NULL DEFAULT 1,
    snapshot_version INTEGER NOT NULL DEFAULT 0,
    last_error_code VARCHAR(80) NULL,
    last_error_message TEXT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_by UUID NULL,
    approved_by UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_publications_status ON publications(status);
CREATE INDEX IF NOT EXISTS idx_publications_platform ON publications(platform);
CREATE INDEX IF NOT EXISTS idx_publications_created_at ON publications(created_at DESC);

CREATE TABLE IF NOT EXISTS publication_media_bundles (
    id UUID PRIMARY KEY,
    publication_id UUID NOT NULL REFERENCES publications(id) ON DELETE CASCADE,
    bundle_type VARCHAR(32) NOT NULL,
    asset_manifest JSONB NOT NULL,
    primary_asset_url TEXT NULL,
    render_status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_publication_media_bundles_publication_id
    ON publication_media_bundles(publication_id);

CREATE TABLE IF NOT EXISTS publication_attempts (
    id UUID PRIMARY KEY,
    publication_id UUID NOT NULL REFERENCES publications(id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL,
    trigger_type VARCHAR(24) NOT NULL,
    request_id VARCHAR(120) NULL,
    workflow_run_id VARCHAR(120) NULL,
    status VARCHAR(24) NOT NULL,
    remote_status VARCHAR(80) NULL,
    remote_post_id VARCHAR(255) NULL,
    error_code VARCHAR(80) NULL,
    error_message TEXT NULL,
    payload_hash VARCHAR(128) NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ NULL,
    UNIQUE (publication_id, attempt_number)
);

CREATE INDEX IF NOT EXISTS idx_publication_attempts_publication_id
    ON publication_attempts(publication_id);

CREATE TABLE IF NOT EXISTS publication_reviews (
    id UUID PRIMARY KEY,
    publication_id UUID NOT NULL REFERENCES publications(id) ON DELETE CASCADE,
    action VARCHAR(32) NOT NULL,
    actor_user_id UUID NULL,
    comment TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_publication_reviews_publication_id
    ON publication_reviews(publication_id);

CREATE TABLE IF NOT EXISTS publication_snapshots (
    id UUID PRIMARY KEY,
    publication_id UUID NOT NULL REFERENCES publications(id) ON DELETE CASCADE,
    snapshot_type VARCHAR(32) NOT NULL,
    payload JSONB NOT NULL,
    version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (publication_id, version)
);

CREATE INDEX IF NOT EXISTS idx_publication_snapshots_publication_id
    ON publication_snapshots(publication_id);
