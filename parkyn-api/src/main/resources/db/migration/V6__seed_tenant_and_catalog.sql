-- V6 : tenant par défaut, catalogue de permissions, rôles système

INSERT INTO tenants (id, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default');

-- Catalogue : 22 permissions, toutes de portée TENANT.
INSERT INTO permissions (code, scope, description) VALUES
    ('reservation:read:own', 'TENANT', 'Lire ses propres réservations'),
    ('reservation:read:all', 'TENANT', 'Lire toutes les réservations'),
    ('reservation:create:own', 'TENANT', 'Créer ses propres réservations'),
    ('reservation:create:all', 'TENANT', 'Créer toutes les réservations'),
    ('reservation:update:own', 'TENANT', 'Mettre à jour ses propres réservations'),
    ('reservation:update:all', 'TENANT', 'Mettre à jour toutes les réservations'),
    ('reservation:cancel:own', 'TENANT', 'Annuler ses propres réservations'),
    ('reservation:cancel:all', 'TENANT', 'Annuler toutes les réservations'),
    ('reservation:return:all', 'TENANT', 'Retourner toutes les réservations'),
    ('vehicle:read', 'TENANT', 'Lire les véhicules'),
    ('vehicle:create', 'TENANT', 'Créer des véhicules'),
    ('vehicle:update', 'TENANT', 'Mettre à jour les véhicules'),
    ('vehicle:status:update', 'TENANT', 'Mettre à jour le statut des véhicules'),
    ('user:read:own', 'TENANT', 'Lire son propre profil'),
    ('user:read:all', 'TENANT', 'Lire tous les profils'),
    ('user:create', 'TENANT', 'Créer des profils'),
    ('user:update', 'TENANT', 'Mettre à jour les profils'),
    ('user:status:update', 'TENANT', 'Mettre à jour le statut des profils'),
    ('user:roles:assign', 'TENANT', 'Assigner des rôles aux utilisateurs'),
    ('roles:manage', 'TENANT', 'Gérer les rôles'),
    ('notification:read:own', 'TENANT', 'Lire ses propres notifications'),
    ('dashboard:read:all', 'TENANT', 'Lire tous les tableaux de bord');

-- Rôles système : tenant_id NULL
INSERT INTO roles (tenant_id, name) VALUES
    (NULL, 'ADMIN'),
    (NULL, 'AGENT'),
    (NULL, 'DRIVER');

-- Un INSERT ... SELECT par rôle, avec la liste des codes qu'il reçoit :
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'reservation:read:own',
    'reservation:create:own',
    'reservation:update:own',
    'reservation:cancel:own',
    'vehicle:read',
    'user:read:own',
    'notification:read:own'
)
WHERE r.name = 'DRIVER' AND r.tenant_id IS NULL;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'reservation:read:all',
    'reservation:create:all',
    'reservation:update:all',
    'reservation:cancel:all',
    'reservation:return:all',
    'vehicle:read',
    'vehicle:create',
    'vehicle:update',
    'vehicle:status:update',
    'user:read:all',
    'notification:read:own',
    'dashboard:read:all'
)
WHERE r.name = 'AGENT' AND r.tenant_id IS NULL;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'reservation:read:all',
    'reservation:create:all',
    'reservation:update:all',
    'reservation:cancel:all',
    'reservation:return:all',
    'vehicle:read',
    'vehicle:create',
    'vehicle:update',
    'vehicle:status:update',
    'user:read:all',
    'user:create',
    'user:update',
    'user:status:update',
    'user:roles:assign',
    'roles:manage',
    'notification:read:own',
    'dashboard:read:all'
)
WHERE r.name = 'ADMIN' AND r.tenant_id IS NULL;