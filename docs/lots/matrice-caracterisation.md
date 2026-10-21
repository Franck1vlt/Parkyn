# Matrice de caractérisation : autorisations du lot 1a

- **Photographie de :** code actuel, avant A3 (`hasRole`)
- **Doit repasser à l'identique après :** A3 (`hasAuthority`)
- **Test associé :** `AuthorizationMatrixTest`

## Légende

| Valeur | Signification |
| ------ | ------------- |
| `OK`   | La sécurité laisse passer : statut ni 400, ni 401, ni 403 (un 404 sur un id inexistant compte comme OK) |
| `403`  | Refusé par la sécurité, que l'appelant soit authentifié ou anonyme (voir « Écarts mesurés ») |

Résultat de la première exécution : 104 cas, tous conformes à cette matrice.

## Matrice

| #                 | Méthode | URL                                | Corps                  | ADMIN | AGENT | DRIVER | Anonyme | Permission cible |
| ----------------- | ------- | ---------------------------------- | ---------------------- | ----- | ----- | ------ | ------- | --- |
| **Auth**          |
| 1                 | POST    | `/api/v1/auth/login`               | `LoginRequest`         | OK    | OK    | OK     | OK      | aucune (public) |
| 2                 | POST    | `/api/v1/auth/refresh`             | `RefreshRequest`       | OK    | OK    | OK     | OK      | aucune (public) |
| 3                 | POST    | `/api/v1/auth/register`            | `RegisterRequest`      | OK    | OK    | OK     | OK      | aucune (public) |
| 4                 | POST    | `/api/v1/auth/logout`              | -                      | OK    | OK    | OK     | `403`      | `isAuthenticated()` |
| 5                 | GET     | `/api/v1/auth/me`                  | -                      | OK    | OK    | OK     | `403`      | `isAuthenticated()` |
| **Dashboard**     |
| 6                 | GET     | `/api/v1/dashboard`                | -                      | OK    | OK    | `403`     | `403`      | `dashboard:read:all` |
| **Notifications** |
| 7                 | GET     | `/api/v1/notifications`            | -                      | OK    | OK    | OK     | `403`      | `notification:read:own` |
| 8                 | PATCH   | `/api/v1/notifications/{id}/read`  | -                      | OK    | OK    | OK     | `403`      | `notification:read:own` |
| 9                 | PATCH   | `/api/v1/notifications/read-all`   | -                      | OK    | OK    | OK     | `403`      | `notification:read:own` |
| **Réservations**  |
| 10                | GET     | `/api/v1/reservations`             | -                      | OK    | OK    | `403`     | `403`      | `reservation:read:all` |
| 11                | POST    | `/api/v1/reservations`             | `ReservationRequest`   | OK    | OK    | `403`     | `403`      | `reservation:create:all` |
| 12                | GET     | `/api/v1/reservations/{id}`        | -                      | OK    | OK    | `403`     | `403`      | `reservation:read:all` |
| 13                | POST    | `/api/v1/reservations/{id}/return` | `ReturnVehicleRequest` | OK    | OK    | `403`     | `403`      | `reservation:return:all` |
| **Utilisateurs**  |
| 14                | GET     | `/api/v1/users`                    | -                      | OK    | OK    | `403`     | `403`      | `user:read:all` |
| 15                | POST    | `/api/v1/users`                    | `UserRequest`          | OK    | OK    | `403`     | `403`      | `user:create` |
| 16                | GET     | `/api/v1/users/{id}`               | -                      | OK    | OK    | `403`     | `403`      | `user:read:all` |
| 17                | PATCH   | `/api/v1/users/{id}`               | `UserUpdateRequest`    | OK    | OK    | `403`     | `403`      | `user:update` |
| 18                | DELETE  | `/api/v1/users/{id}`               | -                      | OK    | OK    | `403`     | `403`      | `user:status:update` |
| 19                | PATCH   | `/api/v1/users/{id}/reactivate`    | -                      | OK    | OK    | `403`     | `403`      | `user:status:update` |
| 20                | GET     | `/api/v1/users/drivers`            | -                      | OK    | OK    | `403`     | `403`      | `user:read:all` |
| **Véhicules**     |
| 21                | GET     | `/api/v1/vehicles`                 | -                      | OK    | OK    | OK     | `403`      | `vehicle:read` |
| 22                | POST    | `/api/v1/vehicles`                 | `VehicleRequest`       | OK    | `403` | `403`     | `403`      | `vehicle:create` |
| 23                | GET     | `/api/v1/vehicles/{id}`            | -                      | OK    | OK    | OK     | `403`      | `vehicle:read` |
| 24                | PATCH   | `/api/v1/vehicles/{id}`            | `VehicleUpdateRequest` | OK    | OK    | `403`     | `403`      | `vehicle:update` |
| 25                | DELETE  | `/api/v1/vehicles/{id}`            | -                      | OK    | `403` | `403`     | `403`      | `vehicle:delete` |
| 26                | PATCH   | `/api/v1/vehicles/{id}/status`     | `VehicleStatusRequest` | OK    | OK    | `403`     | `403`      | `vehicle:status:update` |

**Colonne « Permission cible »** : la permission que l'endpoint exigera après A3.

- **†** : la permission cible **change le comportement** (le DRIVER gagne l'accès aux véhicules et à ses notifications ; l'AGENT perd la création et la suppression de véhicules). Ces endpoints gardent `hasRole` pendant la bascule A3, puis passent à leur permission dans une étape séparée « changements de comportement », avec mise à jour de cette matrice.
- Ligne 14 (`GET /users`) : décision produit, l'AGENT peut voir la liste de tous les comptes (il ne peut créer, modifier ou désactiver que des conducteurs, grâce à `UserAccessPolicy`). Changement de comportement appliqué : l'AGENT est passé de `403` à `OK` (voir « Changements de comportement »).
- `user:create`, `user:update` et `user:status:update` sont accordées à l'AGENT par la V11 : la permission ouvre la gestion des comptes, `UserAccessPolicy` la limite aux conducteurs.

## Changements de comportement

Modifications volontaires de la matrice, décidées après la bascule A3. Chaque case modifiée est listée ici.

| Date | Lignes | Case | Avant | Après | Raison |
| ---- | ------ | ---- | ----- | ----- | ------ |
| 2026-10-21 | 7, 8, 9 (notifications) | DRIVER | `403` | OK | Le conducteur a un espace personnel et reçoit ses propres notifications (`notification:read:own`). La portée est garantie par `findByIdAndUserId` et le filtre par utilisateur |
| 2026-10-21 | 21, 23 (lecture des véhicules) | DRIVER | `403` | OK | Le conducteur consulte les véhicules pour réserver (`vehicle:read`) |
| 2026-10-21 | 14 (`GET /users`) | AGENT | `403` | OK | L'agent consulte la liste de tous les comptes (`user:read:all`). Il ne crée, modifie ou désactive que des conducteurs (`UserAccessPolicy`). La réponse ne contient aucune donnée sensible (pas de hash de mot de passe) |
| 2026-10-21 | 22 (`POST /vehicles`) | AGENT | OK | `403` | Seul l'ADMIN ajoute des véhicules au parc (`vehicle:create` retirée à l'AGENT par la V12) |
| 2026-10-21 | 25 (`DELETE /vehicles/{id}`) | AGENT | OK | `403` | Seul l'ADMIN retire un véhicule du parc (`vehicle:delete`, réservée à l'ADMIN depuis la V11). L'AGENT garde la modification et le statut |

Remarque : aujourd'hui, `NotificationService` ne crée de notifications que pour les ADMIN et les AGENT. Un DRIVER obtient donc une liste vide tant qu'aucune notification ne lui est destinée.

Plus aucune case n'est marquée † : tous les endpoints protégés utilisent une permission, sauf `/auth/me` et `/auth/logout` (`isAuthenticated()`).

À prévoir côté frontend (phase D3) : masquer « Ajouter un véhicule » et « Supprimer » pour l'AGENT, qui reçoit désormais `403`.

## Failles connues (contrôle sur l'objet, hors matrice)

La matrice ne mesure que l'accès à l'endpoint. Ces cas sont couverts par des tests dédiés.

| #   | Scénario | Avant correction | Après correction | Statut | Test |
| --- | -------- | ---------------- | ---------------- | ------ | ---- |
| F1  | AGENT modifie un compte ADMIN (`PATCH /users/{id}`) | `200` : la modification est appliquée (mot de passe, rôle...) | `403`, et l'ADMIN est inchangé en base | Corrigée | `UserControllerTest.testAgentUpdateUserById` |
| F2  | AGENT modifie un DRIVER avec `"role": "ADMIN"` | `200` : le DRIVER devient ADMIN (escalade de privilèges) | `403`, et le DRIVER reste DRIVER en base | Corrigée | `UserControllerTest.testAgentUpdateDriverWithAdminRole` |

Cause commune : `UserService.updateUserById` ne recevait pas l'utilisateur courant et ne contrôlait ni la cible ni le rôle demandé, contrairement à `addUser` et `deleteUserById`.

Correction : `UserAccessPolicy` centralise les deux contrôles sur l'objet et les applique à la lecture, la création, la modification, la suppression et la réactivation :

- `decide(actor, action, target)` : un AGENT n'agit que sur des comptes DRIVER (lecture de tous les comptes autorisée, comme avant) ;
- `decideRoleAssignment(actor, requestedRole)` : un AGENT n'attribue que le rôle DRIVER, et un rôle absent de la requête n'est pas une attribution.

`UserService` traduit la décision (`AccessDecision`, interface scellée) : `Denied` donne `403`, `NotFound` donne `404`. La matrice des 104 cas est restée identique après la correction.

### Changement de comportement à traiter

Depuis la correction, un AGENT ne peut plus modifier **son propre** compte (la cible n'est pas un DRIVER). Décision produit : un AGENT doit pouvoir modifier son nom et son mot de passe, mais pas son rôle, que seul un ADMIN peut changer. À implémenter dans `UserAccessPolicy` (cas où l'acteur est la cible), avec un test dédié, puis à exprimer par une permission `user:update:own` lors de A3.

## Écarts constatés

### Prédictions faites avant le test

- **Anonyme : `403` et non `401`.** `SecurityConfig` ne déclare aucun `authenticationEntryPoint`, ni `httpBasic`, ni `formLogin`. Dans ce cas, Spring Security utilise par défaut `Http403ForbiddenEntryPoint` : un appel sans token devrait recevoir `403` partout, y compris sur `/auth/logout` et `/auth/me` (ouverts par `permitAll` au niveau URL, puis refusés par `@PreAuthorize("isAuthenticated()")`). Confirmé par le test.
- **Corps invalide avant sécurité.** La validation `@Valid @RequestBody` s'exécute avant `@PreAuthorize`. Un DRIVER qui envoie un corps invalide sur un endpoint interdit reçoit `400` au lieu de `403`. Le test doit donc envoyer des corps valides pour les lignes 1, 2, 3, 11, 13, 15, 17, 22, 24 et 26.

### Divergence entre le code actuel et le catalogue de l'ADR-0002

Aujourd'hui, le DRIVER n'a accès qu'à `/auth/me` et `/auth/logout`. Le catalogue de permissions (V6) lui donne pourtant `vehicle:read`, `reservation:read:own`, `reservation:create:own`, `reservation:update:own`, `reservation:cancel:own`, `user:read:own` et `notification:read:own`.

Conséquence pour A3 : la bascule `hasRole` → `hasAuthority` doit d'abord reproduire **exactement** cette matrice (refactoring sans changement de comportement). L'ouverture des endpoints au DRIVER est un changement de comportement volontaire, à faire dans une étape séparée, en mettant à jour cette matrice et en documentant chaque case modifiée.

### Écarts mesurés

Première exécution de `AuthorizationMatrixTest` : 104 cas (26 endpoints × 4 acteurs).

| Constat | Explication | Suite donnée |
| --- | --- | --- |
| Anonyme refusé avec `403`, jamais `401` | Prédiction confirmée : aucun `authenticationEntryPoint` configuré, Spring Security utilise `Http403ForbiddenEntryPoint` | Colonne Anonyme passée à `403`. La sémantique HTTP voudrait `401` : à traiter avec les cookies (phase C) |
| DRIVER sur `POST /reservations` : `400` au lieu de `403` | Corps de test invalide (`vehicleId` nul : `@MethodSource` est appelé avant `@BeforeEach`). La validation passe avant `@PreAuthorize` | Corps corrigé (UUID constant). Un `400` fait désormais échouer tout cas `OK` |
| AGENT sur `POST /users` : `403` alors que l'endpoint lui est ouvert | `403` levé par la règle métier de `UserService.addUser` (« un AGENT ne crée que des DRIVER »), indiscernable d'un refus de sécurité | Corps de test avec `role = DRIVER` |
| ADMIN et AGENT sur `PATCH /notifications/{id}/read` : erreur `500` | `NotificationService.markAsRead` levait une `RuntimeException` non traitée | Corrigé : `404`. La portée était déjà correcte (`findByIdAndUserId`) |
| `/auth/login` et `/auth/refresh` : `401` | Réponse métier (identifiants ou token inconnus), pas un refus de sécurité | Données valides créées dans `setUp()` à partir de constantes |
