# Lot 1a : checklist

**Statut :** en cours · **Estimation :** ≈ 38 h 30 (5 j-h) · **ADR :** [ADR-0002](../adr/0002-rbac-a-permissions.md)

## Décisions

- [x] Un DRIVER peut modifier/annuler sa propre réservation avant son début (`reservation:write:own`, condition « avant le début » dans le service)
- [x] Lot 1a : rôles système seulement (rôles personnalisés au lot 4)
- [x] `tenant_id` dès maintenant, avec un seul tenant par défaut
- [x] Mécanisme deny by default : les deux (test en CI sur chaque endpoint + config `permitAll` / `/api/v1/**` authenticated / `denyAll` pour le reste)
- [ ] Implémenter le test en CI et l'annotation `@PublicEndpoint` (en A3)
- [x] Matrice rôles × permissions (voir ADR-0002) : 22 permissions, le retour d'un véhicule est réservé aux agents

## Livrables de conception

- [ ] Tableau endpoints → permissions (23 endpoints) avec la portée (`own` / `all` / `n/a`)
- [ ] Plan de migration Expand and Contract (numéros Flyway, source de vérité pendant la cohabitation, retour arrière à chaque étape)
- [x] Matrice de caractérisation : 26 endpoints × 4 acteurs (104 cas), écrite sur le code actuel ([matrice](matrice-caracterisation.md), `AuthorizationMatrixTest`)
- [ ] Comptes de test : 2 DRIVER dans le même tenant + 1 utilisateur d'un autre tenant
- [ ] ADR-0002 relu et réécrit avec mes mots (statut : proposé → accepté)

## Phase A : cadrage et données

- [x] A1 ADR et diagrammes (séquences login, refresh, inscription)
- [x] A2 Migrations Flyway V5 à V10 (`tenants`, `roles`, `permissions`, `role_permissions`, `user_roles`, `verification_tokens`, `user_sessions`, colonnes `tenant_id`, `status`, `created_by`, `updated_by`, emails normalisés)
- [ ] A3 Rôles et permissions (`hasRole` → `hasAuthority`, tests adaptés). La matrice doit repasser à l'identique ; l'ouverture des endpoints au DRIVER se fait dans une étape séparée
- [ ] A4 Hachage Argon2id

## Phase B : inscription et emails

- [ ] B1 Service d'envoi d'emails
- [ ] B2 Inscription
- [ ] B3 Vérification
- [ ] B4 Limitation de débit de base

## Phase C : cookies, CSRF et sessions

- [ ] C1 Cookies HttpOnly
- [ ] C2 CSRF
- [ ] C3 Rotation des refresh tokens
- [ ] C4 Sessions actives

## Phase D : frontend

- [ ] D1 Intercepteur
- [ ] D2 Inscription/vérification
- [ ] D3 Guards par permission
- [ ] D4 Page « Mes sessions »

## Phase E : qualité

- [x] E1 Tests d'intégration : Testcontainers PostgreSQL, `@IntegrationTest` / `@RepositoryTest`, isolation par `TRUNCATE`, tests de migrations et de repositories
- [ ] E2 Documentation et CI

## Travaux réalisés hors phases

- [x] Records pour tous les DTO, copie défensive des listes (`DashboardResponse`)
- [x] `AccessDecision` (interface scellée : `Granted`, `Denied`, `NotFound`) et `UserAction`
- [x] `UserAccessPolicy` : contrôle de la cible et anti-escalade de rôle (failles F1 et F2 corrigées)
- [x] Emails normalisés en minuscules (`User.normalizeEmail`, V10)
- [x] Réservation sans agent visible (`agent` nullable dans l'entité)
- [x] Notification introuvable : 404 au lieu de 500

## Migration Contract (après la bascule du code)

- [ ] Mapper `tenantId` dans `User`, `Vehicle`, `Reservation`, `Notification` **avant** de retirer les `DEFAULT`
- [ ] Retirer les `DEFAULT` temporaires (`users.status`, `tenant_id` des 4 tables)
- [ ] Supprimer `users.role`, `users.is_active`, la table `refresh_tokens` et son index doublon `idx_refresh_tokens_token`
- [ ] Supprimer l'ancien `UNIQUE` sensible à la casse sur `users.email`

## A3 : avancement

- [x] Entités `Role` et `Permission`, relations `User.roles` et `Role.permissions`
- [x] Tests migrés vers `TestUsers` (rôle hérité + ligne `user_roles`)
- [x] Bascule `hasRole` → `hasAuthority` : réservations, tableau de bord, utilisateurs (sauf `GET /users`), véhicules (`PATCH /{id}`, `PATCH /{id}/status`). Preuve : `AuthorityMigrationTest`
- [x] `user_roles` écrit à chaque création ou changement de rôle (`RoleAssignmentService`, `RoleAssignmentTest`)
- [x] Cache des permissions (`@Cacheable` + Caffeine, 60 s, clé = id utilisateur) et révocation au changement de rôle (`PermissionCacheTest`)
- [x] Test « deny by default » et annotation `@PublicEndpoint`
- [x] Changements de comportement (8 cases de la matrice, V12) : notifications et véhicules ouverts au DRIVER, liste des comptes ouverte à l'AGENT, création et suppression de véhicules réservées à l'ADMIN
- [ ] Contract partiel : plus de `ROLE_` dans les autorités, suppression de `users.role`, `AuthorityMigrationTest` et `setRole` dans `TestUsers`

## Points notés en chemin

- [ ] Cache des permissions : retirer l'entrée **après** le commit de la transaction (`@TransactionalEventListener`) pour éviter qu'une requête concurrente remette en cache l'ancien rôle
- [ ] Délégation générale « on ne délègue que ce qu'on possède » (permissions de l'acteur ⊇ permissions du rôle attribué) : avec les rôles personnalisés (lot 4)
- [ ] Méta-annotation `@ApiIntegrationTest` (`@IntegrationTest` + `@AutoConfigureMockMvc`) pour ne plus oublier MockMvc
- [ ] `TestRequests` : corps partagés réservés aux tests où le contenu n'importe pas ; écrire dans le test les corps dont le contenu est le sujet
- [ ] Profil utilisateur : `PATCH /users/me` (prénom, nom) et `POST /users/me/password` (mot de passe actuel exigé), permission `user:update:own`. Décision : un AGENT modifie son nom et son mot de passe, seul l'ADMIN change son rôle. À faire après A3
- [ ] Changement de mot de passe : révoquer les autres sessions (`user_sessions`, phase C)
- [ ] `hasConflict` : exclure la réservation en cours de modification (`excludeId`) quand la modification de réservation existera
- [x] Mapper `createdBy`, `updatedBy`, `updatedAt` (`@UpdateTimestamp`) dans `Reservation` (audit renseigné à la création et au retour)
- [ ] `updateUserById` : vérifier qu'un nouvel email n'est pas déjà pris (409 au lieu de 500)
- [ ] Ajouter un test du tableau de bord (`DashboardService`), absent de la suite
- [ ] `DashboardService` : `@Transactional` sur une méthode privée, sans effet
- [ ] Supprimer le dernier `System.out.println` (`UserDetailsServiceImpl` : email à chaque requête). Ceux de `JwtAuthFilter`, dont le token, sont retirés
- [ ] Anonyme : renvoyer `401` plutôt que `403` (`authenticationEntryPoint`, phase C)
- [ ] Séparer tests unitaires et d'intégration (Surefire / Failsafe) avec la CI (E2)
- [ ] Invitations de conducteurs dans le tenant de leur entreprise (lot 4, nouvel ADR)

## Tests de portée

- [ ] DRIVER liste ses réservations : seulement les siennes
- [ ] DRIVER demande la réservation d'un autre : 404
- [ ] AGENT liste : voit les deux DRIVER, pas l'autre tenant
- [ ] AGENT modifie la réservation d'un DRIVER : succès, `updated_by` renseigné
- [ ] Identifiant inexistant et identifiant d'autrui : réponses identiques
- [ ] DRIVER modifie sa réservation avant le début : succès ; après le début : refusé

## Lot terminé quand

- [ ] Inscription, email reçu, compte activé
- [ ] Aucun token visible dans l'onglet Application du navigateur
- [ ] Requête sans token CSRF rejetée
- [ ] Réutilisation d'un refresh token : session révoquée
- [ ] La matrice de caractérisation repasse à l'identique après migration
- [ ] CI verte, lot déployé
