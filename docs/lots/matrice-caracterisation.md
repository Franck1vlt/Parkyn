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

| #                 | Méthode | URL                                | Corps                  | ADMIN | AGENT | DRIVER | Anonyme |
| ----------------- | ------- | ---------------------------------- | ---------------------- | ----- | ----- | ------ | ------- |
| **Auth**          |
| 1                 | POST    | `/api/v1/auth/login`               | `LoginRequest`         | OK    | OK    | OK     | OK      |
| 2                 | POST    | `/api/v1/auth/refresh`             | `RefreshRequest`       | OK    | OK    | OK     | OK      |
| 3                 | POST    | `/api/v1/auth/register`            | `RegisterRequest`      | OK    | OK    | OK     | OK      |
| 4                 | POST    | `/api/v1/auth/logout`              | -                      | OK    | OK    | OK     | `403`      |
| 5                 | GET     | `/api/v1/auth/me`                  | -                      | OK    | OK    | OK     | `403`      |
| **Dashboard**     |
| 6                 | GET     | `/api/v1/dashboard`                | -                      | OK    | OK    | `403`     | `403`      |
| **Notifications** |
| 7                 | GET     | `/api/v1/notifications`            | -                      | OK    | OK    | `403`     | `403`      |
| 8                 | PATCH   | `/api/v1/notifications/{id}/read`  | -                      | OK    | OK    | `403`     | `403`      |
| 9                 | PATCH   | `/api/v1/notifications/read-all`   | -                      | OK    | OK    | `403`     | `403`      |
| **Réservations**  |
| 10                | GET     | `/api/v1/reservations`             | -                      | OK    | OK    | `403`     | `403`      |
| 11                | POST    | `/api/v1/reservations`             | `ReservationRequest`   | OK    | OK    | `403`     | `403`      |
| 12                | GET     | `/api/v1/reservations/{id}`        | -                      | OK    | OK    | `403`     | `403`      |
| 13                | POST    | `/api/v1/reservations/{id}/return` | `ReturnVehicleRequest` | OK    | OK    | `403`     | `403`      |
| **Utilisateurs**  |
| 14                | GET     | `/api/v1/users`                    | -                      | OK    | `403`    | `403`     | `403`      |
| 15                | POST    | `/api/v1/users`                    | `UserRequest`          | OK    | OK    | `403`     | `403`      |
| 16                | GET     | `/api/v1/users/{id}`               | -                      | OK    | OK    | `403`     | `403`      |
| 17                | PATCH   | `/api/v1/users/{id}`               | `UserUpdateRequest`    | OK    | OK    | `403`     | `403`      |
| 18                | DELETE  | `/api/v1/users/{id}`               | -                      | OK    | OK    | `403`     | `403`      |
| 19                | PATCH   | `/api/v1/users/{id}/reactivate`    | -                      | OK    | OK    | `403`     | `403`      |
| 20                | GET     | `/api/v1/users/drivers`            | -                      | OK    | OK    | `403`     | `403`      |
| **Véhicules**     |
| 21                | GET     | `/api/v1/vehicles`                 | -                      | OK    | OK    | `403`     | `403`      |
| 22                | POST    | `/api/v1/vehicles`                 | `VehicleRequest`       | OK    | OK    | `403`     | `403`      |
| 23                | GET     | `/api/v1/vehicles/{id}`            | -                      | OK    | OK    | `403`     | `403`      |
| 24                | PATCH   | `/api/v1/vehicles/{id}`            | `VehicleUpdateRequest` | OK    | OK    | `403`     | `403`      |
| 25                | DELETE  | `/api/v1/vehicles/{id}`            | -                      | OK    | OK    | `403`     | `403`      |
| 26                | PATCH   | `/api/v1/vehicles/{id}/status`     | `VehicleStatusRequest` | OK    | OK    | `403`     | `403`      |

## Failles connues (contrôle sur l'objet, hors matrice)

La matrice ne mesure que l'accès à l'endpoint. Ces cas sont couverts par des tests dédiés.

| #   | Scénario                                            | Comportement actuel                                           | Comportement attendu                                                     | Test                                         |
| --- | --------------------------------------------------- | ------------------------------------------------------------- | ------------------------------------------------------------------------ | -------------------------------------------- |
| F1  | AGENT modifie un compte ADMIN (`PATCH /users/{id}`) | `200` : la modification est appliquée (mot de passe, rôle...) | `403` : un AGENT ne modifie que des comptes DRIVER                       | `UserControllerTest.testAgentUpdateUserById` |
| F2  | AGENT modifie un DRIVER avec `"role": "ADMIN"`      | `200` : le DRIVER devient ADMIN (escalade de privilèges)      | `403` : on ne peut attribuer qu'un rôle que l'on est autorisé à déléguer | `AuthorizationMatrixTest`                    |

Cause commune : `UserService.updateUserById` ne reçoit pas l'utilisateur courant et ne contrôle ni la cible ni le rôle demandé, contrairement à `addUser` et `deleteUserById`. Correction prévue : `UserAccessPolicy` (`AccessDecision`), dans un commit dédié, après la mise en place de cette matrice.

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
