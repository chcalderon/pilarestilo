CREATE TABLE IF NOT EXISTS role_permissions (
    id BIGSERIAL PRIMARY KEY,
    role VARCHAR(50) NOT NULL,
    view_key VARCHAR(100) NOT NULL,
    UNIQUE (role, view_key)
);

CREATE INDEX IF NOT EXISTS idx_role_permissions_role ON role_permissions(role);
