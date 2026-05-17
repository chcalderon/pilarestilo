CREATE TABLE navigation_sections (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  root_category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
  layout          VARCHAR(24) NOT NULL DEFAULT 'COLUMNS',
  column_count    INT NOT NULL DEFAULT 4 CHECK (column_count BETWEEN 1 AND 6),
  banner_image_url VARCHAR(500),
  banner_title    VARCHAR(120),
  banner_subtitle VARCHAR(240),
  banner_link     VARCHAR(500),
  active          BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order      INT NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (root_category_id)
);
CREATE INDEX idx_nav_sections_order ON navigation_sections(sort_order);
