-- The reset flow moves from a tokenised link to a 6-digit code. The code is low-entropy on
-- purpose (paired with a 30-minute TTL and single use); attempt_count caps blind guesses at
-- PasswordResetToken.MAX_ATTEMPTS. token_hash now stores hash(code) — same column, same shape.
ALTER TABLE password_reset_tokens ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
