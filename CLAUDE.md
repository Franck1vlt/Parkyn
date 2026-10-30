# Parkyn

Application SaaS de gestion de flotte de véhicules et de réservations, multi-entreprises (multi-tenant).
Projet solo, dépôt public. Priorité : code propre, outils open source courants en entreprise.

## Stack

- Backend : Java 21, Spring Boot 4.0.5, Spring Security, PostgreSQL 16, Flyway, JWT (jjwt), Jackson 3 (`tools.jackson.*`)
- Sécurité : Argon2id (Bouncy Castle), Bucket4j + Caffeine (limitation de débit), Spring Mail + Thymeleaf (emails)
- Tests backend : JUnit 5, MockMvc, Testcontainers (PostgreSQL), GreenMail
- Frontend : Angular 21, Tailwind 4, Prettier
- Infra locale : Docker Compose (`db`, `api`, `web`, `nginx`, `mailpit`)
- Infra prod (prévue) : Podman rootless + Traefik sur un VPS OVH
- Copilot IA (prévu) : LLM via API + MCP, pas de modèle local pour l'instant

## Structure

- `parkyn-api/` : API Spring Boot (package `app.parkyn`)
  - `controller/`, `service/`, `repository/`, `model/`, `dto/`
  - `security/` : `SecurityConfig`, `JwtAuthFilter`, `RateLimitFilter`, `RateLimiter`, `ClientIpResolver`, `@PublicEndpoint`
  - `security/authorization/` : politiques d'accès (`UserAccessPolicy`, `ReservationScope`)
  - `tenant/` : résolution du tenant courant (`TenantContext`, `CurrentTenantResolver`)
  - `email/` : envoi asynchrone, gabarits dans `src/main/resources/templates/email/`
  - `src/main/resources/db/migration/` : migrations Flyway (`V<n>__description.sql`)
- `parkyn-web/` : application Angular (environnements dans `src/environments/`, remplacés via `fileReplacements`)
- `nginx/nginx.conf` : point d'entrée unique sur le port 80 ; `/` vers le front, `/api/` vers l'API, transmet `X-Real-IP`
- `docs/adr/` : décisions d'architecture (0001 cookies HttpOnly, 0002 RBAC à permissions, 0003 pas de Keycloak)
- `docs/diagrams/` : séquences Mermaid (inscription, login, refresh, limitation de débit, schéma)
- `docs/lots/lot-1a-checklist.md` : avancement du lot en cours et points notés pour plus tard
- `docs/lots/matrice-caracterisation.md` : matrice d'autorisation et journal daté des changements
- `docker-compose.yml` : environnement local ; variables dans `.env` (modèle : `.env.example`)

## Commandes

- Lancer l'environnement : `docker compose up -d --build` (app sur http://localhost, Mailpit sur http://localhost:8025)
- Après un changement de l'API : reconstruire `api`, puis `docker compose restart nginx` (nginx garde l'ancienne IP du conteneur)
- Tests backend : `cd parkyn-api && ./mvnw clean test` (Windows : `.\mvnw.cmd clean test`, `JAVA_HOME` sur un JDK 21)
- Un seul test : `./mvnw test -Dtest=RateLimitTest`
- Build backend : `cd parkyn-api && ./mvnw verify`
- Tests frontend : `cd parkyn-web && npm test`
- Format frontend : `cd parkyn-web && npx prettier --check src` (pas de script `lint` pour l'instant)
- Docker doit tourner pour les tests backend (Testcontainers).
- Si l'IDE signale une classe introuvable alors que le code compile : `./mvnw clean test`.
- Lance les tests concernés avant de dire qu'une tâche est terminée, et indique le nombre de tests passés.

## Invariants d'architecture (à ne jamais enfreindre)

1. **Multi-tenant** : toute table métier a un `tenant_id`. Toute requête, tout service et tout endpoint filtre par tenant. Une fuite de données entre entreprises est le bug le plus grave.
2. **RBAC en base** : le catalogue de permissions est fixé au déploiement (migration de seed) ; les rôles et attributions sont choisis par l'admin de chaque entreprise. Pas d'enum de rôles en dur. L'enum `User.Role` est un reste de l'ancien modèle : ne pas l'étendre ni s'en servir pour une décision d'accès.
3. **Permissions côté serveur** : elles sont chargées côté serveur, jamais stockées dans le JWT. Chaque endpoint vérifie explicitement la permission requise (`@PreAuthorize("hasAuthority('ressource:action:portée')")`). Refus par défaut : un endpoint public est marqué `@PublicEndpoint` et déclaré dans `SecurityConfig`.
4. **Sessions et sécurité (lot 1a)** : cookies HttpOnly, protection CSRF, vérification d'email à l'inscription. Aucun token dans `localStorage` (le front y stocke encore les tokens : à corriger en phase D).
5. **Flyway** : ne jamais modifier une migration déjà appliquée ou commitée sur `main`. Toute évolution de schéma = nouvelle migration, versionnée à la suite.
6. **Pas d'énumération de comptes** : inscription et renvoi du lien répondent toujours `202`, que l'adresse existe ou non ; le login répond le même `401` pour tous les échecs.
7. **IP du client** : `X-Real-IP` n'est cru que s'il vient d'un proxy de `parkyn.rate-limit.trusted-proxies`. Ne jamais lire l'IP autrement que par `ClientIpResolver`.

## Conventions

- Backend : couches controller → service → repository. Pas de logique métier dans les controllers. DTO (records) en entrée et sortie, jamais d'entité JPA exposée. JSON en `snake_case` via `@JsonProperty`.
- Validation des entrées par Bean Validation sur les DTO ; emails normalisés (trim + minuscules) côté service.
- Configuration dans `application.properties` sous le préfixe `parkyn.*` ; toute nouvelle variable d'environnement est ajoutée à `.env.example`.
- Frontend : composants standalone, services pour l'accès API, pas d'appel HTTP direct dans les composants.
- Tests :
  - tout nouveau comportement métier ou règle de permission est couvert par un test, y compris le cas « autre tenant » et le cas « permission manquante » ;
  - tests d'intégration annotés `@IntegrationTest` (base vidée par `sql/cleanup.sql` et seaux de débit vidés avant chaque test) ;
  - utilisateurs créés via `TestUsers` ; tout nouvel endpoint est ajouté à `AuthorizationMatrixTest` ;
  - un test de limitation de débit utilise sa propre IP (`127.0.0.x`) ;
  - un test rouge doit échouer pour la bonne raison (pas un `400` de corps invalide quand on attend un `401`).
- Code et identifiants en anglais ; messages utilisateur et commentaires en français.
- Pas de nouvelle dépendance sans me le dire : préférer l'existant et l'open source.
- Documentation : à la fin d'une étape, mettre à jour la checklist du lot, la matrice (entrée datée) et le diagramme concerné.

## Git et déploiement

- Je travaille sur `dev`, fusionné dans `main`. Ne jamais pousser directement sur `main`.
- Le VPS tire `main` en lecture seule. Ne touche pas aux fichiers de déploiement ou de prod sans me demander.
- Messages de commit courts, en français.

## Façon de travailler

- TDD : d'abord un test rouge qui échoue pour la bonne raison, puis le code.
- Sur une tâche non triviale : proposer d'abord un plan court avec les décisions à valider.
- Petits changements ciblés. Pas de refactor non demandé.
- Si une demande contredit un invariant ci-dessus, le signaler au lieu de l'appliquer.
- Ne jamais lire ni chercher les fichiers `.env` : utiliser `.env.example`.
- Ne jamais committer de secrets (clés API, mots de passe, `.env`).
- Les préférences personnelles de travail sont dans `CLAUDE.local.md` (non versionné).
