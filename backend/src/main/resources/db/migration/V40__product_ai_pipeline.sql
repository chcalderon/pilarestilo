CREATE TABLE product_ai_drafts (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  product_id UUID REFERENCES products(id),
  status VARCHAR(24) NOT NULL,
  name VARCHAR(255),
  brand VARCHAR(255),
  condition VARCHAR(16),
  price_amount NUMERIC(15,2),
  price_currency VARCHAR(10),
  created_by UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE product_ai_assets (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  draft_id UUID NOT NULL REFERENCES product_ai_drafts(id) ON DELETE CASCADE,
  original_url TEXT NOT NULL,
  processed_master_url TEXT,
  processed_web_url TEXT,
  processed_thumb_url TEXT,
  source_filename VARCHAR(255),
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE product_ai_jobs (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  draft_id UUID NOT NULL REFERENCES product_ai_drafts(id) ON DELETE CASCADE,
  status VARCHAR(24) NOT NULL,
  progress INT NOT NULL DEFAULT 0,
  attempt INT NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL DEFAULT 3,
  next_retry_at TIMESTAMP WITH TIME ZONE,
  error_code VARCHAR(100),
  error_message TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  started_at TIMESTAMP WITH TIME ZONE,
  finished_at TIMESTAMP WITH TIME ZONE,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE product_ai_outputs (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  job_id UUID NOT NULL REFERENCES product_ai_jobs(id) ON DELETE CASCADE,
  asset_id UUID NOT NULL REFERENCES product_ai_assets(id) ON DELETE CASCADE,
  title VARCHAR(255),
  description TEXT,
  image_prompt TEXT,
  raw_response_json TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_ai_drafts_status ON product_ai_drafts(status);
CREATE INDEX idx_product_ai_drafts_created_by ON product_ai_drafts(created_by);
CREATE INDEX idx_product_ai_assets_draft_sort ON product_ai_assets(draft_id, sort_order);
CREATE INDEX idx_product_ai_jobs_status_next_retry ON product_ai_jobs(status, next_retry_at);
CREATE INDEX idx_product_ai_jobs_draft_created ON product_ai_jobs(draft_id, created_at DESC);
CREATE INDEX idx_product_ai_outputs_job ON product_ai_outputs(job_id);
