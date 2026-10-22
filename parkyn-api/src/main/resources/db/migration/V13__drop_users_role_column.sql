-- V13 : Contract partiel A3. Le rôle n'est plus stocké que dans user_roles.
--       Le code ne lit et n'écrit plus users.role (User.getRole() est déduit de user_roles).
--       Ce qui reste à supprimer au Contract final (phase C)
--       - users.is_active
--       - la table refresh_tokens (avec son index doublon)
--       - les DEFAULT temporaires de status et des tenant_id

-- supprimer la colonne role de la table users
ALTER TABLE users DROP COLUMN role;
