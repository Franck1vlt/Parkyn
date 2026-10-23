# Lot 1a : checklist

**Statut :** en cours · **Estimation :** ≈ 38 h 30 (5 j-h) · **ADR :** [ADR-0002](../adr/0002-rbac-a-permissions.md)

## Décisions

- [x] Un DRIVER peut modifier/annuler sa propre réservation avant son début (`reservation:write:own`, condition « avant le début » dans le service)
- [x] Lot 1a : rôles système seulement (rôles personnalisés au lot 4)
- [x] `tenant_id` dès maintenant, avec un seul tenant par défaut
- [x] Mécanisme deny by default : les deux (test en CI sur chaque endpoint + config `permitAll` / `/api/v1/**` authenticated / `denyAll` pour le reste)
- [x] Implémenter le test en CI et l'annotation `@PublicEndpoint` (en A3) : `DenyByDefaultTest`, `SecurityConfigTest`, `ErrorPageSecurityTest`
- [x] Matrice rôles × permissions (voir ADR-0002) : 22 permissions, le retour d'un véhicule est réservé aux agents

## Livrables de conception

- [ ] Tableau endpoints → permissions (23 endpoints) avec la portée (`own` / `all` / `n/a`)
- [ ] Plan de migration Expand and Contract (numéros Flyway, source de vérité pendant la cohabitation, retour arrière à chaque étape)
- [x] Matrice de caractérisation : 26 endpoints × 4 acteurs (104 cas), écrite sur le code actuel ([matrice](matrice-caracterisation.md), `AuthorizationMatrixTest`)
- [x] Comptes de test : 2 DRIVER dans le même tenant (`ReservationScopeTest`) + une entreprise B complète (`TenantIsolationTest`, `TestUsers.inTenant`)
- [ ] ADR-0002 relu et réécrit avec mes mots (statut : proposé → accepté)

## Phase A : cadrage et données

- [x] A1 ADR et diagrammes (séquences login, refresh, inscription)
- [x] A2 Migrations Flyway V5 à V10 (`tenants`, `roles`, `permissions`, `role_permissions`, `user_roles`, `verification_tokens`, `user_sessions`, colonnes `tenant_id`, `status`, `created_by`, `updated_by`, emails normalisés)
- [x] A3 Rôles et permissions (`hasRole` → `hasAuthority`, tests adaptés). La matrice est repassée à l'identique ; les 8 changements de comportement sont documentés dans la matrice
- [x] A3+ Tenant : `@TenantId` d'Hibernate sur `User`, `Vehicle`, `Reservation`, `Notification` (filtre automatique de toutes les requêtes, `findById` compris, et tenant renseigné à l'insertion). `CurrentTenantResolver` : tenant de l'utilisateur connecté, contexte racine sans filtre hors connexion (connexion, inscription, traitement planifié), exception dans tout autre cas. Inscription rattachée au tenant par défaut, traitement planifié exécuté tenant par tenant (`TenantContext`). Autre tenant = `404`, comme un id inexistant. Preuves : `TenantIsolationTest`, `CurrentTenantResolverTest`
- [ ] A3+ Portée `own` / `all` : `ReservationScope` (filtre dans la requête, 404 hors portée) et espace réservations du DRIVER (voir « Tests de portée »)
- [x] A4 Hachage Argon2id : `DelegatingPasswordEncoder` (Argon2id par défaut, hash sans préfixe = BCrypt), conversion à la connexion via `UserDetailsPasswordService`, paramètres OWASP configurables (`parkyn.security.argon2.*`), `PasswordHashingTest`

## Phase B : inscription et emails

- [ ] B1 Service d'envoi d'emails
- [ ] B2 Inscription
- [ ] B3 Vérification
- [ ] B4 Limitation de débit de base. Prioritaire sur `/auth/login` : chaque tentative calcule un Argon2id (~19 Mio), une rafale de tentatives peut saturer la mémoire du serveur

## Phase C : cookies, CSRF et sessions

- [ ] C1 Cookies HttpOnly
- [ ] C2 CSRF
- [ ] C3 Rotation des refresh tokens
- [ ] C4 Sessions actives

## Phase D : frontend

- [ ] Avant D1 : audit complet de la suite de tests (faux verts, assertions faibles, tests qui passent pour une autre raison que leur nom)
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

- [x] Mapper `tenantId` dans `User`, `Vehicle`, `Reservation`, `Notification` **avant** de retirer les `DEFAULT` (`@TenantId`, A3+ Tenant)
- [ ] Retirer les `DEFAULT` temporaires (`users.status`, `tenant_id` des 4 tables). Plus aucun code applicatif n'en dépend (`@TenantId`) ; à adapter : les insertions SQL des tests (`FlywayMigrationTest`)
- [x] Supprimer `users.role` (V13, Contract partiel A3 ; le rôle est déduit de `user_roles`)
- [ ] Supprimer `users.is_active`, la table `refresh_tokens` et son index doublon `idx_refresh_tokens_token` (Contract final, après C3)
- [ ] Supprimer l'ancien `UNIQUE` sensible à la casse sur `users.email`

## A3 : avancement

- [x] Entités `Role` et `Permission`, relations `User.roles` et `Role.permissions`
- [x] Tests migrés vers `TestUsers` (rôle hérité + ligne `user_roles`)
- [x] Bascule `hasRole` → `hasAuthority` : réservations, tableau de bord, utilisateurs (sauf `GET /users`), véhicules (`PATCH /{id}`, `PATCH /{id}/status`). Preuve : `AuthorityMigrationTest`
- [x] `user_roles` écrit à chaque création ou changement de rôle (`RoleAssignmentService`, `RoleAssignmentTest`)
- [x] Cache des permissions (`@Cacheable` + Caffeine, 60 s, clé = id utilisateur) et révocation au changement de rôle (`PermissionCacheTest`)
- [x] Test « deny by default » et annotation `@PublicEndpoint`
- [x] Changements de comportement (8 cases de la matrice, V12) : notifications et véhicules ouverts au DRIVER, liste des comptes ouverte à l'AGENT, création et suppression de véhicules réservées à l'ADMIN
- [x] Contract partiel : plus de `ROLE_` dans les autorités, suppression de `users.role` (V13), `AuthorityMigrationTest` et `setRole` dans `TestUsers`
- [x] Autorisation : `@PreAuthorize` + `hasAuthority`, permissions chargées côté serveur (cache). `PermissionEvaluator` non retenu : le contrôle sur l'objet est fait par `UserAccessPolicy` dans les services (à réévaluer avec `ReservationScope`)

## Points notés en chemin

- [ ] Connexion d'un compte désactivé : `DisabledException` n'est pas traitée par `GlobalExceptionHandler`, la réponse diffère du 401 standard et révèle qu'un compte désactivé existe. Répondre le même 401 « Email ou mot de passe incorrect »
- [ ] Timing de connexion : tant que des comptes gardent un hash BCrypt, leur vérification est plus rapide qu'un Argon2id, ce qui peut révéler les comptes non convertis (risque faible, décroît avec les conversions)
- [ ] Déploiement : mesurer le temps d'un hash Argon2id sur le serveur de production (cible 200 à 500 ms) et ajuster `ARGON2_MEMORY_KIB` / `ARGON2_ITERATIONS` ; dimensionner la RAM selon les connexions simultanées
- [ ] Piste : implémentation native d'Argon2 (liaison vers la bibliothèque C) pour réduire l'écart de performance avec un attaquant ; non nécessaire aujourd'hui (Bouncy Castle, Java pur)
- [x] Disponibilité des véhicules par période (V14, `VehicleAvailabilityTest`) : seules les périodes réservées décident ([début, fin[, ou [début, retour[ si rendu en avance). `RESERVED` n'est plus stocké mais calculé (réservation en cours, ou commencée et pas encore retournée) ; `RESERVED` manuel : `422`. Filtre `GET /vehicles?from&to` des véhicules libres sur une plage. Demandes simultanées traitées l'une après l'autre (verrou sur le véhicule) avec contrainte `EXCLUDE` `reservations_no_overlap` en filet de sécurité : la seconde reçoit `409` « choisissez un autre véhicule ou une autre plage horaire »
- [ ] Frontend (phase D) : la recherche de véhicules à réserver envoie `status=AVAILABLE` seul, qui veut dire « pas dehors maintenant ». Envoyer la plage `from` / `to` de la réservation, en UTC (`toISOString()`, suffixe `Z`) : un `+02:00` non encodé dans l'URL devient un espace et donne `400`
- [ ] Verrou des réservations sans délai maximal : une transaction bloquée fait attendre toutes les demandes sur ce véhicule. Ajouter un `lock_timeout` (≈ 5 s) et répondre `409` « réessayez »
- [ ] Mises à jour concurrentes d'un véhicule (retour et changement de statut en même temps) : la dernière écrase les autres champs (pas de `@Version`). Prendre le même verrou (`findByIdForUpdate`) dans `returnVehicle`, `updateVehicleStatus` et `updateVehicleById`
- [ ] Filtre par période : la liste des véhicules occupés part en `NOT IN (...)`, sans limite de taille. Remplacer par une sous-requête si le parc dépasse quelques milliers de véhicules
- [ ] Un véhicule peut passer en `MAINTENANCE` pendant une location ou avec des réservations futures, sans avertissement : à signaler à l'agent (phase D)
- [x] Modification et annulation des réservations (V15, `ReservationChangeTest`) : `PATCH /reservations/{id}` (début, fin, notes) et `POST /reservations/{id}/cancel`. DRIVER : sa réservation, avant le début ; AGENT, ADMIN : tout le tenant, annulation avant le début, modification tant que non retournée, début figé une fois commencée. Annulation douce (`cancelled_at`, `cancelled_by`) : statut `CANCELLED` dans l'historique, créneau libéré, notifications de la réservation remplacées par une notification `RESERVATION_CANCELLED` aux agents et administrateurs. Statut calculé dans la réponse (`UPCOMING`, `IN_PROGRESS`, `RETURNED`, `CANCELLED`) et filtre `?status=` (statut inconnu : `400`)
- [ ] Frontend (phase D) : afficher le `status` des réservations. Aujourd'hui, une réservation annulée apparaît « en cours » (action de retour proposée tant que `returnedAt` est vide), l'API répond alors `409`
- [ ] Horloge injectée (`Clock`) : les règles « avant le début » utilisent `OffsetDateTime.now()`, la limite exacte (annulation à l'instant du début) n'est pas testable
- [ ] `notesBefore` ne peut pas être effacé par `PATCH` (`null` = inchangé) ; tableau de bord : les réservations annulées restent dans « dernières réservations » et « derniers conducteurs » (historique)
- [x] Retard (V16, `ReservationOnHoldTest`) : statut calculé `ON_HOLD` quand une réservation antérieure du véhicule n'est ni retournée ni annulée ; retour à `IN_PROGRESS` dès le retour. En attente, le véhicule n'a pas été remis : l'agent peut la reporter ou l'annuler, le DRIVER reste bloqué après la date de début. Notification `RESERVATION_ON_HOLD` aux agents et administrateurs, une fois par réservation (vérification toutes les 15 minutes, tenant par tenant)
- [x] Remise du véhicule (V17, `ReservationHandoverTest`) : `POST /reservations/{id}/handover` (AGENT, ADMIN, `reservation:handover:all`), kilométrage saisi, état de départ repris du véhicule, à partir du rendez-vous de début et jusqu'au rendez-vous de fin. « En cours » = véhicule remis ; avant, statut `AWAITING_HANDOVER` (ou `ON_HOLD` si le véhicule est dehors avec une autre réservation). Véhicule dehors = remis et pas retourné. Retour impossible sans remise ; conducteur absent : l'agent annule une réservation non remise, même après le rendez-vous. Le kilométrage n'est plus enregistré à la création. Réservations déjà commencées ou retournées considérées comme remises (H5)
- [ ] Frontend (phase D) : écran de remise des clés (kilométrage). Sans lui, une réservation créée après la V17 ne peut pas être retournée depuis l'interface
- [ ] ADR-0002 : le catalogue passe à 24 permissions (`reservation:handover:all`, V17)
- [ ] Rôles personnalisés (lot 4) : un rôle qui a `reservation:return:all` doit aussi avoir `reservation:handover:all`, sinon il ne pourra jamais retourner un véhicule (le retour exige une remise)
- [ ] Mises à jour concurrentes d'un véhicule : la remise écrit aussi le kilométrage du véhicule ; un `PATCH /vehicles/{id}` simultané peut l'écraser (voir le point sur `findByIdForUpdate` dans `VehicleService`)
- [ ] Traitements planifiés avec plusieurs instances de l'application : aucun verrou (ShedLock) ni index unique (utilisateur, réservation, type), des notifications en double sont possibles. Une erreur dans un tenant arrête aussi les tenants suivants du même passage
- [ ] Statut `ON_HOLD` : l'ensemble des réservations en attente du tenant est chargé à chaque liste et au tableau de bord (`IN (...)`). Passer à une sous-requête `EXISTS` dans la `Specification` si le volume grandit
- [ ] Réservation reportée puis de nouveau en attente : pas de seconde notification (une seule par réservation, comme décidé)
- [ ] Contract de `RESERVED` : retirer la valeur de l'enum et de la contrainte `CHECK` de `vehicles.status` quand le frontend ne l'enverra plus comme statut stocké (phase D)
- [ ] Déploiement : la V14 crée l'extension `btree_gist` (extension « trusted » depuis PostgreSQL 13 : il faut le droit `CREATE` sur la base). Elle échoue si des réservations se chevauchent déjà en base, avec leur liste : les corriger avant de relancer
- [ ] API des réservations en Expand and Contract : `conditionBefore`, `mileageStart` et `agentId` deviennent facultatifs et ignorés (remplis par le serveur), puis le frontend cesse de les envoyer (phase D), puis suppression du DTO
- [ ] Rôle dérivé de `user_roles` : tout `getRole()` hors transaction sur un utilisateur chargé sans ses rôles lève `LazyInitializationException`. `findByEmail` charge les rôles (`@EntityGraph`) ; attention aux traitements planifiés et aux tests qui relisent la base
- [ ] Lot 4 : remplacer les tests de nom de rôle (inventaire lignes 6 à 12 : `UserAccessPolicy`, `DashboardService`, `NotificationService`) par des permissions et par la règle de délégation (permissions de la cible incluses dans celles de l'acteur, avec « `all` inclut `own` » appliqué dans le code, sans dupliquer les permissions en base)
- [ ] Cache des permissions : retirer l'entrée **après** le commit de la transaction (`@TransactionalEventListener`) pour éviter qu'une requête concurrente remette en cache l'ancien rôle
- [ ] Délégation générale « on ne délègue que ce qu'on possède » (permissions de l'acteur ⊇ permissions du rôle attribué) : avec les rôles personnalisés (lot 4)
- [ ] Méta-annotation `@ApiIntegrationTest` (`@IntegrationTest` + `@AutoConfigureMockMvc`) pour ne plus oublier MockMvc
- [ ] `TestRequests` : corps partagés réservés aux tests où le contenu n'importe pas ; écrire dans le test les corps dont le contenu est le sujet
- [ ] Profil utilisateur : `PATCH /users/me` (prénom, nom) et `POST /users/me/password` (mot de passe actuel exigé), permission `user:update:own`. Décision : un AGENT modifie son nom et son mot de passe, seul l'ADMIN change son rôle. À faire après A3
- [ ] Changement de mot de passe : révoquer les autres sessions (`user_sessions`, phase C)
- [x] `hasConflict` : exclure la réservation en cours de modification (`excludeId`)
- [x] Mapper `createdBy`, `updatedBy`, `updatedAt` (`@UpdateTimestamp`) dans `Reservation` (audit renseigné à la création et au retour)
- [ ] `updateUserById` : vérifier qu'un nouvel email n'est pas déjà pris, avec `existsByEmailInAnyTenant` (409 au lieu de 500)
- [ ] Ajouter un test du tableau de bord (`DashboardService`), absent de la suite
- [ ] `DashboardService` : `@Transactional` sur une méthode privée, sans effet
- [ ] Supprimer le dernier `System.out.println` (`UserDetailsServiceImpl` : email à chaque requête). Ceux de `JwtAuthFilter`, dont le token, sont retirés
- [ ] Anonyme : renvoyer `401` plutôt que `403` (`authenticationEntryPoint`, phase C)
- [ ] Séparer tests unitaires et d'intégration (Surefire / Failsafe) avec la CI (E2)
- [ ] Invitations de conducteurs dans le tenant de leur entreprise (lot 4, nouvel ADR)
- [ ] Requêtes SQL natives : non filtrées par `@TenantId`. Toute nouvelle `nativeQuery` doit filtrer `tenant_id` elle-même ; seule exception voulue : `existsByEmailInAnyTenant` (emails uniques dans toute l'application). Row-Level Security PostgreSQL en défense en profondeur au lot 4
- [ ] Plaques d'immatriculation uniques dans toute l'application (index de la V2) : une plaque déjà enregistrée par une autre entreprise répond `500`. Décider au lot 4 (unique par tenant, ou `409` explicite)
- [ ] Liens entre tenants non bloqués par la base : les clés étrangères portent sur `id` seul, une réservation de B pourrait référencer un véhicule ou un conducteur de A (aujourd'hui empêché par l'application : `findById` filtré, testé). Défense en profondeur au lot 4 : `UNIQUE (tenant_id, id)` sur les tables parentes et clés étrangères composites `(tenant_id, vehicle_id)`, etc. Une telle donnée incohérente donne une `500` (entité liée introuvable), jamais une fuite
- [ ] Emails uniques dans toute l'application : un `409` à la création révèle qu'un email existe dans une autre entreprise (comme l'inscription). À consigner dans l'ADR des invitations (lot 4)
- [x] Endpoints publics d'authentification appelés avec un token : `JwtAuthFilter` les ignore (`shouldNotFilter`, chemins partagés avec `SecurityConfig`), sinon la session prenait le tenant du token
- [ ] Nouveau traitement sans utilisateur connecté (tâche planifiée, file de messages) : l'exécuter dans `TenantContext.runAs(tenant, ...)` avec une nouvelle transaction, jamais en contexte racine

## Tests de portée

- [x] DRIVER liste ses réservations : seulement les siennes
- [x] DRIVER demande la réservation d'un autre : 404
- [ ] AGENT liste : voit les deux DRIVER, pas l'autre tenant
- [x] AGENT modifie la réservation d'un DRIVER : succès, `updated_by` renseigné
- [x] Identifiant inexistant et identifiant d'autrui : réponses identiques
- [x] DRIVER modifie sa réservation avant le début : succès ; après le début : refusé

## Lot terminé quand

- [ ] Inscription, email reçu, compte activé
- [ ] Aucun token visible dans l'onglet Application du navigateur
- [ ] Requête sans token CSRF rejetée
- [ ] Réutilisation d'un refresh token : session révoquée
- [ ] La matrice de caractérisation repasse à l'identique après migration
- [ ] CI verte, lot déployé
