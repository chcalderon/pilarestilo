-- Issuing a boleta is its own responsibility, so it gets its own permissions.
--
-- The owner intends to hand this work to an administrative person rather than keep doing it as
-- ADMIN. ADMINISTRACION already reviews payments since V70, and registering the document that
-- follows the payment belongs to the same desk, so no new role is invented here.
--
-- Voiding is separated from issuing on purpose: undoing a tax document is not the same act as
-- creating one, even though the same people happen to hold both today.
INSERT INTO permissions (code, name, description, module, category) VALUES
    ('documents.read',  'Ver documentos tributarios',    'Consultar boletas y facturas de una venta',    'billing', 'read'),
    ('documents.issue', 'Registrar documentos',          'Registrar la boleta o factura de una venta',   'billing', 'workflow'),
    ('documents.void',  'Anular documentos',             'Anular un documento tributario emitido',       'billing', 'workflow')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission_grants (role, permission_code) VALUES
    ('ADMIN',          'documents.read'),
    ('ADMIN',          'documents.issue'),
    ('ADMIN',          'documents.void'),
    ('ADMINISTRACION', 'documents.read'),
    ('ADMINISTRACION', 'documents.issue'),
    ('ADMINISTRACION', 'documents.void'),
    ('SUPERVISOR',     'documents.read'),
    ('SELLER',         'documents.read')
ON CONFLICT DO NOTHING;

-- The sales screen these permissions guard reads orders, and orders.read has been seeded since V63
-- without a single holder outside ADMIN and SELLER. ADMINISTRACION and SUPERVISOR cannot approve a
-- payment or register its boleta while being unable to read the order it belongs to.
INSERT INTO role_permission_grants (role, permission_code) VALUES
    ('ADMINISTRACION', 'orders.read'),
    ('SUPERVISOR',     'orders.read')
ON CONFLICT DO NOTHING;
