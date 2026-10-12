-- V7 : statut, tenant et rôles des utilisateurs (Expand + Backfill)

-- 1. Colonnes nullables d'abord
ALTER TABLE users
    ADD COLUMN status    VARCHAR(30),
    ADD COLUMN tenant_id UUID;

-- 2. Backfill
UPDATE users 
SET tenant_id = '00000000-0000-0000-0000-000000000001';

UPDATE users
SET status = CASE
    WHEN is_active = TRUE THEN 'ACTIVE'
    ELSE 'DISABLED'
END;

UPDATE users
SET status = 'DISABLED'
WHERE email IS NULL;

-- 3. Contraintes, une fois les données remplies
ALTER TABLE users
    ALTER COLUMN status    SET DEFAULT 'ACTIVE',
    ALTER COLUMN status    SET NOT NULL,
    ALTER COLUMN tenant_id SET DEFAULT '00000000-0000-0000-0000-000000000001',
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT fk_users_tenant_id
    FOREIGN KEY (tenant_id) REFERENCES tenants(id);

CREATE INDEX idx_users_tenant_id ON users(tenant_id);

ALTER TABLE users
    ADD CONSTRAINT chk_users_status
    CHECK (status IN ('PENDING_EMAIL_VERIFICATION', 'ACTIVE', 'DISABLED'));

ALTER TABLE users
    ADD CONSTRAINT chk_users_email_status
    CHECK (email IS NOT NULL OR status = 'DISABLED');

CREATE UNIQUE INDEX idx_users_email_lower ON users (lower(email));

-- 4. user_roles à partir de users.role
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = u.role AND r.tenant_id IS NULL;
