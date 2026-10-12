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
- [ ] Matrice de caractérisation 3 rôles × 23 endpoints (200 ou 403), écrite sur le code **actuel**
- [ ] Comptes de test : 2 DRIVER dans le même tenant + 1 utilisateur d'un autre tenant
- [ ] ADR-0002 relu et réécrit avec mes mots (statut : proposé → accepté)

## Phase A : cadrage et données

- [ ] A1 ADR et diagrammes (séquences login, refresh, inscription)
- [ ] A2 Migrations Flyway (`tenants`, `roles`, `permissions`, `role_permissions`, `user_roles`, colonnes `tenant_id`, `driver_id`, `created_by`, `updated_by`)
- [ ] A3 Rôles et permissions (`hasRole` → `hasAuthority`, tests adaptés)
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

- [ ] E1 Tests d'intégration
- [ ] E2 Documentation et CI

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
