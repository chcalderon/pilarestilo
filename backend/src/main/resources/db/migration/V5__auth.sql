-- Fast email lookup for auth
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
