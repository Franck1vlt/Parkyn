# ADR-0002 : RBAC à permissions (Parkyn v2, lot 1a)

|               |                                                                     |
| ------------- | ------------------------------------------------------------------- |
| **Statut**    | Proposé (à relire et réécrire avec mes propres mots avant adoption) |
| **Lot**       | 1a : Fondations de l'authentification (tâche A1)                    |
| **Dépend de** | Parkyn v1 (enum `Role`, 23 `@PreAuthorize`)                         |
| **Prépare**   | Lot 2 (copilot IA / MCP) et lot 4 (multi-tenant)                    |

---

## 1. Contexte

Parkyn v1 contrôle l'accès avec `enum Role { ADMIN, AGENT, DRIVER }`, appliqué via 23 annotations `@PreAuthorize`.

Deux lots à venir rendent ce modèle insuffisant :

- **Lot 4 (multi-entreprises)** : chaque entreprise cliente veut ses propres rôles.
- **Lot 2 (copilot IA)** : un agent agit au nom d'un utilisateur via des outils MCP, avec moins de droits que lui.

Un troisième besoin existe déjà : **la portée**. Un DRIVER ne doit voir que ses propres réservations, alors qu'un AGENT doit pouvoir reprendre le travail d'un autre agent (absence, maladie) et donc voir et modifier toutes les réservations.

---

## 2. Q1 : Limites de l'enum quand chaque entreprise veut ses propres rôles

### Problème

L'enum fige trois rôles dans le code :

- **Qui décide :** seul le développeur peut créer un rôle, via un déploiement. Une entreprise ne peut pas créer « Comptable » elle-même.
- **Sens unique :** « AGENT » a le même sens pour tous les clients. L'entreprise B ne peut pas retirer l'annulation à ses agents si l'entreprise A la garde.
- **Couplage :** ce que fait un rôle est dispersé dans 23 annotations. Changer un droit oblige à toucher 23 endroits.
- **Un seul rôle par utilisateur :** impossible d'être à la fois « Agent » et « Comptable ».

### Options

1. Garder l'enum.
2. Rôles en base, sans permissions (les droits restent dans le code).
3. Rôles **et** permissions en base.

### Décision

**Option 3 : rôles et permissions en base.**

Pourquoi : au lot 4 (multi-entreprises), l'admin d'une entreprise doit pouvoir créer ses propres rôles en combinant des permissions. Ces permissions forment un catalogue prédéfini par moi, le développeur, et livré par migration Flyway. L'entreprise choisit les combinaisons, mais ne peut pas inventer de permission.

Ce qu'on livre dans le lot 1a :

- **Seulement les rôles système** (ADMIN, AGENT, DRIVER), créés par migration. Les rôles personnalisés par entreprise arriveront au lot 4.
- **Le catalogue de permissions**, au format `ressource:action` (avec une portée quand c'est pertinent, voir plus bas).
- **Plusieurs rôles par utilisateur** via une table `user_roles`. Les permissions d'un utilisateur sont l'**union** de celles de ses rôles.
- **`tenant_id` dès maintenant :** un seul tenant par défaut, créé par la migration, auquel tous les utilisateurs existants sont rattachés. On ajoutera d'autres tenants au fur et à mesure, sans migration lourde du schéma.

Dans `roles`, `tenant_id` est nul pour un rôle système et rempli pour un futur rôle personnalisé. Les rôles système ne sont modifiables que par migration, après revue.

### Portée des permissions

Le principe : un système simple, sûr et adapté au besoin, sans complexité inutile. Les contrôles sont centralisés et placés au plus près de la donnée.

Quand une ressource appartient à un utilisateur, la permission porte sa portée dans son nom :

- `ressource:action:own` : les éléments dont je suis propriétaire.
- `ressource:action:all` : tous les éléments de mon entreprise.

Règles et raisons :

- **Deux niveaux seulement** (`own` et `all`), pour éviter une complexité inutile. On n'en ajoutera un troisième que si un client le demande.
- **`all` inclut `own`.** Un utilisateur qui a `read:all` n'a pas besoin de `read:own` : on ne duplique pas les permissions.
- **Le propriétaire d'une réservation est le conducteur** (`driver_id`), pas l'agent qui la saisit.
- **Les agents ont la portée `all`.** N'importe quel agent peut remplacer un collègue absent sans transfert de propriété.
- **Filtrage dans la requête SQL**, jamais en mémoire (par exemple `WHERE tenant_id = :t AND driver_id = :u`). C'est plus sûr, plus performant, et la pagination reste correcte.
- **404 hors portée, pas 403.** Un 403 révélerait que la ressource existe. Un identifiant inexistant et celui d'un autre utilisateur donnent exactement la même réponse.
- **Un composant unique** (`ReservationScope`) centralise ces règles et est appelé depuis les services. On évite ainsi de les disperser dans les contrôleurs, comme les 23 `@PreAuthorize` actuels.
- **Le filtre `tenant_id` est systématique**, quelle que soit la permission. Il prépare le lot 4.

Seules les ressources où la portée a un sens sont concernées (réservations, notifications, profil). Les véhicules sont visibles de tous les rôles de l'entreprise.

### Permissions des rôles système (lot 1a)

Convention `ressource:action[:portée]`. Comme `all` inclut `own`, un AGENT ou un ADMIN n'a pas besoin des permissions `:own`.

| Permission                | DRIVER | AGENT | ADMIN |
| ------------------------- | :----: | :---: | :---: |
| `reservation:read:own`    |   ✅   |       |       |
| `reservation:read:all`    |        |  ✅   |  ✅   |
| `reservation:create:own`  |   ✅   |       |       |
| `reservation:create:all`  |        |  ✅   |  ✅   |
| `reservation:update:own`  |   ✅   |       |       |
| `reservation:update:all`  |        |  ✅   |  ✅   |
| `reservation:cancel:own`  |   ✅   |       |       |
| `reservation:cancel:all`  |        |  ✅   |  ✅   |
| `reservation:return:all`  |        |  ✅   |  ✅   |
| `vehicle:read`            |   ✅   |  ✅   |  ✅   |
| `vehicle:create`          |        |  ✅   |  ✅   |
| `vehicle:update`          |        |  ✅   |  ✅   |
| `vehicle:status:update`   |        |  ✅   |  ✅   |
| `user:read:own` (profil)  |   ✅   |       |       |
| `user:read:all`           |        |  ✅   |  ✅   |
| `user:create`             |        |       |  ✅   |
| `user:update`             |        |       |  ✅   |
| `user:status:update`      |        |       |  ✅   |
| `user:roles:assign`       |        |       |  ✅   |
| `roles:manage`            |        |       |  ✅   |
| `notification:read:own`   |   ✅   |  ✅   |  ✅   |
| `dashboard:read:all`      |        |  ✅   |  ✅   |

Le catalogue compte 22 permissions. Par rapport au brouillon, `user:read` devient `user:read:all` et `user:read:own` est ajoutée, pour que le conducteur lise son propre profil sans voir les autres utilisateurs. `reservation:return:own` est supprimée : le retour d'un véhicule est toujours fait par un agent.

**Décisions de produit :**

- Le DRIVER consulte ses réservations, en crée, les modifie (période) ou les annule, mais seulement **avant qu'elles ne commencent**. Ensuite, il doit demander à un agent.
- Le DRIVER n'a pas accès au dashboard, réservé aux AGENT et ADMIN.
- Les AGENT et ADMIN créent des véhicules et lisent les utilisateurs.
- Le DRIVER ne retourne pas un véhicule lui-même : un agent fait la démarche (état du véhicule, kilométrage) et valide, puis la réservation passe à « terminée » (`reservation:return:all`).

La permission dit _qui_ peut agir, pas _quand_. La condition « avant le début » est une règle métier, vérifiée dans le service de réservation et non dans le catalogue de permissions. Un agent (`update:all`, `cancel:all`) n'est pas soumis à cette limite.

---

## 3. Q2 : Le coût

| Poste                         | Constat                                                                                                                                                                                           | Parade                                                                                                                                                                                                 |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Jointures**                 | Une requête SQL avec 4 jointures (`users → user_roles → roles → role_permissions → permissions`, soit 5 tables), et non 4 requêtes HTTP. REST ou GraphQL n'y change rien : le coût est entre l'API et PostgreSQL | Index sur les clés étrangères, chargement unique mis en cache                                                                                                                                          |
| **Cache**                     | Une perte de droits doit être immédiate, un gain de droits peut attendre quelques secondes                                                                                                        | Caffeine avec un TTL de 30 à 60 s (filet de sécurité) et une éviction à chaque modification. Redis seulement avec une deuxième instance d'API (lot 3). Décision réversible                             |
| **Taille du JWT**             | 40 permissions donnent environ 1,3 à 1,5 Ko ajoutés au header `Authorization` de **chaque** requête (l'access token est en mémoire, ADR-0001, donc la limite des 4 Ko d'un cookie ne s'applique pas ici). Un retrait de permission mettrait jusqu'à 1 h à agir                       | **Pas de permissions dans le JWT d'accès.** Il porte l'utilisateur, le tenant et la session. Les permissions sont résolues côté serveur. Seul le token délégué du copilot les contient (5 min, réduit) |
| **Part « stateless » perdue** | Chaque requête consulte le cache de permissions                                                                                                                                                   | Le JWT reste stateless pour l'identité, seul le jeu de permissions est résolu côté serveur                                                                                                             |
| **Portée (BOLA / IDOR)**      | Sans règle de portée, un DRIVER qui change l'identifiant dans `GET /reservations/42` lit la réservation d'un collègue. Un filtre en mémoire fausse la pagination                                  | Portée dans le nom de la permission, filtre dans la requête SQL, composant unique, 404 hors portée                                                                                                     |
| **Migration**                 | 23 `@PreAuthorize` à passer de `hasRole` à `hasAuthority`, et seulement 4 tests de refus 403 (tous dans `UserControllerTest`)                                                                     | Stratégie **Expand and Contract**, protégée par une matrice de caractérisation (3 rôles × 23 endpoints) écrite **avant** de toucher au code                                                            |
| **Deny by default**           | `anyRequest().authenticated()` laisse un endpoint oublié ouvert à tout utilisateur connecté                                                                                                       | Deux mécanismes complémentaires. **Test en CI** : il échoue si un endpoint n'a ni `@PreAuthorize` ni marquage « public » explicite (ex. `@PublicEndpoint`), y compris `/auth/me` marqué `isAuthenticated()`. **Config** : routes publiques en `permitAll`, `/api/v1/**` en `authenticated`, tout le reste en `denyAll`. Le `denyAll` seul ne suffit pas, car le filtre URL s'exécute avant `@PreAuthorize` : il forcerait à dupliquer chaque URL dans la config                                                                  |
| **Gouvernance**               | Le catalogue grossit avec le produit                                                                                                                                                              | Convention `ressource:action[:portée]`, cible de 15 à 30 permissions, une permission = une **action métier** (pas un endpoint)                                                                         |

---

## 4. Conséquences

**Positives**

- Les rôles deviennent configurables par chaque client sans déploiement.
- Le copilot reçoit exactement les droits nécessaires, et pas plus.
- Le lot 4 (multi-tenant) n'impose plus de refonte du modèle d'accès.
- La portée est exprimée dans le catalogue, donc configurable elle aussi.

**Négatives et dette assumée**

- Plus de tables, plus de migrations et un cache à invalider correctement.
- Chaque requête consulte un cache de permissions (coût mémoire et complexité).
- Le catalogue de permissions devient un contrat à maintenir avec soin.
- **Traçabilité des modifications.** Puisque n'importe quel agent peut modifier la réservation d'un conducteur, chaque réservation enregistre `created_by` et `updated_by`, et chaque modification est écrite dans le journal d'audit. La responsabilité d'une modification doit rester retrouvable : c'est la contrepartie de la souplesse donnée aux agents.

**Limite connue**

- Les règles de portée plus riches (« un conducteur voit les réservations de son équipe », « un agent voit seulement son agence ») ne sont pas couvertes par `own` et `all`. Si un client l'exige, il faudra un moteur de politiques (ReBAC ou ABAC, par exemple OpenFGA ou Cerbos). Inutile aujourd'hui.

---

## 5. Vision (lots 2 et 4)

Ces points ne sont pas implémentés au lot 1a. Ils justifient trois choix déjà faits : permissions fines, portée dans le nom des permissions, `tenant_id` dès maintenant.

### Lot 2 : copilot IA (MCP)

- **Token délégué :** JWT court (5 min, audience `parkyn-mcp`) contenant `permissions de l'utilisateur ∩ permissions accordées au copilot`. La lecture seule est un jeu de grants `*:read`, pas un rôle.
- **Vérification déterministe côté serveur MCP** à chaque appel. Filtrer la liste d'outils montrée au LLM n'est qu'un confort, pas une barrière.
- **Identité lue dans le token,** jamais dans les arguments d'un outil.
- **Écritures confirmées** par l'humain à partir des arguments structurés, et tracées dans le journal d'audit.
- **Ce que le lot 1a doit garantir :** les services restent l'unique source de vérité des droits et de la portée, afin que les outils MCP les réutilisent.

### Lot 4 : multi-tenant

- **Rôles :** rôles système partagés (modèles, modifiés par migration) et rôles personnalisés par tenant (créés par clonage). Une nouvelle permission du catalogue n'est jamais attribuée automatiquement aux rôles personnalisés.
- **Garde-fous de l'administration des rôles :** pas d'escalade de privilèges (on ne délègue que ce qu'on possède), permissions de plateforme jamais attribuables par un admin de tenant, invariant « au moins un utilisateur actif avec `roles:manage` par tenant », propriétaire du tenant transférable.
- **Isolation :** filtre `tenant_id` systématique, avec Row-Level Security PostgreSQL en défense en profondeur.
- **Ce que le lot 1a prépare :** la colonne `tenant_id` (un seul tenant créé par la migration), la colonne `scope` des permissions, la table `user_roles`.

Quand un point de cette vision est implémenté, on le déplace dans les décisions de l'ADR concerné ou dans un nouvel ADR (par exemple le token délégué au lot 2).

---

## 6. Suivi

Les tâches et livrables de ce lot sont suivis dans `docs/lots/lot-1a-checklist.md`.

---

## 7. Références

- OWASP Authorization Cheat Sheet
- OWASP Session Management Cheat Sheet
- OWASP API Security Top 10 (API1 : Broken Object Level Authorization)
- Spring Security Reference (Architecture, Authorization)
- Modèle NIST RBAC (RBAC0 à RBAC3), et notions d'ABAC / ReBAC
- Spécification MCP (Authorization, Security Best Practices)
