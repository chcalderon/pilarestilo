-- The right to be forgotten, as far as the law allows it.
--
-- Ley 21.719 gives the customer the right to have her data deleted; the tax law obliges the shop to
-- keep a boleta for six years. Both are true at once, which is why this queue anonymises rather
-- than deletes: the person stops being identifiable, the documents stay legible. The boleta already
-- carries a snapshot of the buyer's name and email for exactly this moment, taken when it was
-- issued.
--
-- A queue rather than a button, because the shop has to be able to say when it was asked and when
-- it was done, and because the answer is sometimes "not yet": an order in flight has to arrive
-- before the address that receives it can go.

CREATE TABLE data_deletion_requests (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id       UUID NOT NULL REFERENCES users(id),
  status        VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
  reason        VARCHAR(500),
  requested_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

  -- What the shop did about it, and who did it. A refusal without a reason is not an answer.
  resolved_at   TIMESTAMP WITH TIME ZONE,
  resolved_by   UUID REFERENCES users(id),
  resolution    VARCHAR(500),

  created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

  CONSTRAINT chk_deletion_status CHECK (status IN ('REQUESTED', 'ANONYMISED', 'REFUSED')),
  CONSTRAINT chk_deletion_refusal_reason
    CHECK (status <> 'REFUSED' OR resolution IS NOT NULL)
);

-- One open request per person: asking twice is the same ask, and two rows would make the queue
-- lie about how many people are waiting.
CREATE UNIQUE INDEX uq_deletion_open_per_user
  ON data_deletion_requests (user_id)
  WHERE status = 'REQUESTED';

CREATE INDEX idx_deletion_requests_open ON data_deletion_requests (requested_at) WHERE status = 'REQUESTED';

COMMENT ON TABLE data_deletion_requests IS
  'ARCOP deletion queue. Resolving one anonymises the user; it never deletes boletas or payments, which have their own legal retention.';

-- Who may see and resolve them. Same shape as the returns desk: ADMINISTRACION runs it, only ADMIN
-- carries out the anonymisation, which cannot be undone.
INSERT INTO permissions (code, name, description, module, category) VALUES
    ('privacy.read',    'Ver solicitudes de datos', 'Consultar solicitudes de supresion de datos personales', 'privacy', 'read'),
    ('privacy.resolve', 'Resolver supresiones',     'Anonimizar a una persona o rechazar con motivo',         'privacy', 'admin')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission_grants (role, permission_code) VALUES
    ('ADMIN',          'privacy.read'),
    ('ADMIN',          'privacy.resolve'),
    ('ADMINISTRACION', 'privacy.read')
ON CONFLICT DO NOTHING;
