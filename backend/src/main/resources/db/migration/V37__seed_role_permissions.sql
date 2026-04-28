INSERT INTO role_permissions (role, view_key) VALUES
    ('ADMIN',          'dashboard'),
    ('ADMIN',          'productos'),
    ('ADMIN',          'usuarios'),
    ('ADMIN',          'caja'),
    ('ADMIN',          'despachos'),
    ('ADMIN',          'configuracion'),
    ('ADMIN',          'roles_permisos'),
    ('SUPERVISOR',     'dashboard'),
    ('SUPERVISOR',     'caja'),
    ('ADMINISTRACION', 'dashboard'),
    ('ADMINISTRACION', 'usuarios'),
    ('ADMINISTRACION', 'roles_permisos'),
    ('DESPACHADOR',    'dashboard'),
    ('DESPACHADOR',    'despachos'),
    ('SELLER',         'dashboard'),
    ('SELLER',         'productos'),
    ('SELLER',         'caja')
ON CONFLICT (role, view_key) DO NOTHING;
