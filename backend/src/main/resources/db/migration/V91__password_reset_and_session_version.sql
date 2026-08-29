-- Self-service password reset.
--
-- Only the token's hash is stored. A leaked table is then useless without the raw token that
-- only ever lived in the email link, the same principle as storing a password hash rather than
-- the password. The raw token is 256 bits from SecureRandom, base64url in the URL.
--
-- Single use (used_at) and short lived (expires_at, 30 min). Requesting a new link marks every
-- earlier unused row for that user as used, so only the most recent link ever works.

CREATE TABLE password_reset_tokens (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  VARCHAR(64) NOT NULL,                 -- SHA-256, hex
  expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  used_at     TIMESTAMP WITH TIME ZONE,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_password_reset_tokens_hash ON password_reset_tokens (token_hash);
CREATE INDEX idx_password_reset_tokens_user_unused
  ON password_reset_tokens (user_id)
  WHERE used_at IS NULL;

COMMENT ON TABLE password_reset_tokens IS
  'Hash-only, single-use, short-lived password reset links. The raw token lives only in the email.';

-- Bumped on every password change, self-service or admin-forced. Carried in the access and
-- refresh JWTs as the "sv" claim and compared on every authenticated request; a mismatch is
-- rejected exactly like an expired token. This is what makes a reset log every existing session
-- out, on every device, without a token revocation list.
ALTER TABLE users ADD COLUMN session_version INTEGER NOT NULL DEFAULT 1;

COMMENT ON COLUMN users.session_version IS
  'Incremented on every password change. JWTs carry the value they were minted with (claim "sv"); a stale one is rejected like an expired token.';
