-- V5 : tables du modèle RBAC (Expand)

CREATE TABLE tenants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID,
    name        VARCHAR(120) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_roles_system_name ON roles (name) WHERE tenant_id IS NULL;

CREATE UNIQUE INDEX idx_roles_tenant_id_name ON roles (tenant_id, name) WHERE tenant_id IS NOT NULL;

CREATE TABLE permissions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code       VARCHAR(120) NOT NULL UNIQUE,
    scope      VARCHAR(50) NOT NULL CHECK (scope IN ('TENANT', 'PLATFORM')),
    description TEXT
);

CREATE TABLE role_permissions (
    role_id       UUID REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

CREATE TABLE user_roles (
    user_id     UUID REFERENCES users(id) ON DELETE CASCADE,
    role_id     UUID REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);