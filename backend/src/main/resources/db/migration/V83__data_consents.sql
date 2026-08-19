-- What the customer agreed to, and to which version of it.
--
-- The Ley 21.719 asks the shop to be able to show that consent was given, for what purpose, and
-- when. "The user ticked a box" is not that: the text behind the box changes, and without the
-- version stored there is no way to say afterwards what they were shown.
--
-- Append-only for the same reason a boleta is. Withdrawing marks revoked_at on the row; it never
-- deletes it, because "she consented on the 3rd and withdrew on the 9th" is the fact that matters,
-- and a deleted row says neither half.

CREATE TABLE data_consents (
  id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id        UUID NOT NULL REFERENCES users(id),
  consent_type   VARCHAR(20) NOT NULL,
  policy_version VARCHAR(20) NOT NULL,
  accepted_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  revoked_at     TIMESTAMP WITH TIME ZONE,

  -- Evidence, not tracking: the address and agent that submitted the acceptance. Kept because a
  -- consent nobody can place is a consent nobody can prove.
  ip_address     VARCHAR(45),
  user_agent     VARCHAR(300),

  created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

  CONSTRAINT chk_data_consents_type CHECK (consent_type IN ('TERMS', 'PRIVACY', 'MARKETING'))
);

-- One live consent of each kind per user and version. Re-accepting the same version changes
-- nothing; accepting a new version is a new row, which is what makes the history readable.
CREATE UNIQUE INDEX uq_data_consents_live
  ON data_consents (user_id, consent_type, policy_version)
  WHERE revoked_at IS NULL;

CREATE INDEX idx_data_consents_user ON data_consents (user_id, consent_type);

COMMENT ON TABLE data_consents IS
  'Append-only record of what each customer agreed to and under which published version. Ley 21.719.';
COMMENT ON COLUMN data_consents.policy_version IS
  'The version shown at the time, from system_settings.privacy_policy_version / terms_version. Without it the row proves nothing.';

-- The versions currently published. Bumping one is what makes every stored consent "older than
-- the current text", which is the question the shop has to be able to answer.
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS privacy_policy_version VARCHAR(20);
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS terms_version VARCHAR(20);

UPDATE system_settings
   SET privacy_policy_version = COALESCE(privacy_policy_version, '2026-08'),
       terms_version          = COALESCE(terms_version, '2026-08')
 WHERE id = 1;

ALTER TABLE system_settings ALTER COLUMN privacy_policy_version SET DEFAULT '2026-08';
ALTER TABLE system_settings ALTER COLUMN terms_version SET DEFAULT '2026-08';
ALTER TABLE system_settings ALTER COLUMN privacy_policy_version SET NOT NULL;
ALTER TABLE system_settings ALTER COLUMN terms_version SET NOT NULL;
