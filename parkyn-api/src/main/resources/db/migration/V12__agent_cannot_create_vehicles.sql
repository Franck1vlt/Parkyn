-- V12 : seul l'ADMIN crée des véhicules (décision produit, changement de comportement)
--       L'AGENT perd vehicle:create. Il garde vehicle:read, vehicle:update et vehicle:status:update.
--       vehicle:delete n'a jamais été accordée à l'AGENT (V11) : rien à retirer.

DELETE FROM role_permissions
WHERE role_id = (
    SELECT id FROM roles WHERE name = 'AGENT' AND tenant_id IS NULL
)
AND permission_id = (
    SELECT id FROM permissions WHERE code = 'vehicle:create'
);
