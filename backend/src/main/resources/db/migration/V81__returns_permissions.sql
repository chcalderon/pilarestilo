-- Handling a return and paying the money back are different responsibilities.
--
-- Same split as issuing a boleta versus voiding one: registering and undoing are not the same act,
-- and moving money is not the same as moving a garment. ADMINISTRACION runs the desk; only ADMIN
-- releases the refund, as with cancelling a sale.
INSERT INTO permissions (code, name, description, module, category) VALUES
    ('returns.read',   'Ver devoluciones',      'Consultar solicitudes de devolucion y retracto',  'returns', 'read'),
    ('returns.manage', 'Gestionar devoluciones','Aprobar, rechazar, recibir y disponer la prenda', 'returns', 'workflow'),
    ('returns.refund', 'Registrar reembolsos',  'Registrar la devolucion del dinero',              'returns', 'admin')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission_grants (role, permission_code) VALUES
    ('ADMIN',          'returns.read'),
    ('ADMIN',          'returns.manage'),
    ('ADMIN',          'returns.refund'),
    ('ADMINISTRACION', 'returns.read'),
    ('ADMINISTRACION', 'returns.manage'),
    ('SUPERVISOR',     'returns.read'),
    ('SELLER',         'returns.read')
ON CONFLICT DO NOTHING;
