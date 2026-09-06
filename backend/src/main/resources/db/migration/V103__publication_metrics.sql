-- Engagement metrics for a published social post, pulled from the Meta Graph API.
-- One row per publication, upserted on each refresh. NULL metrics mean the platform does not
-- report that number for this post (or a fetch has not populated it yet). fetch_error holds the
-- last failure message (bad token scope, deleted post) so the UI can say "no disponible".
CREATE TABLE publication_metrics (
    publication_id UUID PRIMARY KEY REFERENCES publications (id) ON DELETE CASCADE,
    impressions BIGINT,
    reach       BIGINT,
    likes       BIGINT,
    comments    BIGINT,
    shares      BIGINT,
    saved       BIGINT,
    fetched_at  TIMESTAMPTZ NOT NULL,
    fetch_error TEXT
);
