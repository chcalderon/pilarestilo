ALTER TABLE users
    ADD COLUMN IF NOT EXISTS worker_vigency_start DATE,
    ADD COLUMN IF NOT EXISTS worker_vigency_end DATE;
