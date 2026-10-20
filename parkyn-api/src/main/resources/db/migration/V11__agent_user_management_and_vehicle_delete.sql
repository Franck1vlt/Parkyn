-- V11 : corrections du catalogue avant la bascule A3
--   1. L'AGENT gère les comptes (la politique UserAccessPolicy le limite aux conducteurs)
--   2. Nouvelle permission vehicle:delete, réservée à l'ADMIN

-- 1. Nouvelle permission
INSERT INTO permissions (code, scope, description) VALUES ('vehicle:delete', 'TENANT', 'Retirer un véhicule du parc');

-- 2. Permissions de gestion des comptes pour l'AGENT
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('user:create', 'user:update', 'user:status:update')
WHERE r.name = 'AGENT' AND r.tenant_id IS NULL;

-- 3. vehicle:delete pour l'ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'vehicle:delete'
WHERE r.name = 'ADMIN' AND r.tenant_id IS NULL;
