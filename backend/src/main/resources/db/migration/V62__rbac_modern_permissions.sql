CREATE TABLE IF NOT EXISTS permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    module VARCHAR(60) NOT NULL,
    category VARCHAR(40) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_permissions_module ON permissions(module);
CREATE INDEX IF NOT EXISTS idx_permissions_active ON permissions(active);

CREATE TABLE IF NOT EXISTS role_permission_grants (
    id BIGSERIAL PRIMARY KEY,
    role VARCHAR(50) NOT NULL,
    permission_code VARCHAR(120) NOT NULL,
    source VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (role, permission_code),
    CONSTRAINT fk_role_permission_grants_permission
        FOREIGN KEY (permission_code) REFERENCES permissions(code)
);

CREATE INDEX IF NOT EXISTS idx_role_permission_grants_role ON role_permission_grants(role);
CREATE INDEX IF NOT EXISTS idx_role_permission_grants_permission ON role_permission_grants(permission_code);
