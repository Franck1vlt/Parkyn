-- Nettoyage avant chaque test d'intégration.
--
-- Tables de référence, à NE JAMAIS vider (créées par les migrations) :
--   flyway_schema_history                                    : historique des migrations
--   tenants, roles, permissions, role_permissions             : données de référence de la V6
--
-- Tables de données, vidées avant chaque test (lignes créées par les tests) :
--   users, user_roles, user_sessions, verification_tokens, refresh_tokens,
--   vehicles, reservations, notifications
--
-- Règle : toute nouvelle table ajoutée par une migration doit être classée ici.
--
-- Pas de CASCADE : si une table qui référence une table vidée manque dans la liste,
-- PostgreSQL refuse avec une erreur explicite au lieu de la vider en silence.
-- Pas de RESTART IDENTITY : tous les identifiants sont des UUID, il n'y a aucune séquence.

TRUNCATE TABLE
    notifications,
    reservations,
    vehicles,
    user_sessions,
    verification_tokens,
    refresh_tokens,
    user_roles,
    users;
