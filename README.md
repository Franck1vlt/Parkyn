# Parkyn — Gestion de Flotte & Réservations

Application web full-stack de gestion de flotte de véhicules et de réservations, destinée aux équipes d'agents et administrateurs. Elle couvre l'ensemble du cycle de vie d'une réservation : création, retour de véhicule et historique.

---

## Fonctionnalités clés

| Domaine              | Fonctionnalités                                                                                |
| -------------------- | ---------------------------------------------------------------------------------------------- |
| **Authentification** | JWT (access token + refresh token), révocation, profil utilisateur                             |
| **Tableau de bord**  | Statistiques par rôle, dernières réservations, véhicules en maintenance, tendances mensuelles  |
| **Réservations**     | Création, suivi actif/retourné, rapport de dommages, historique par agent                      |
| **Véhicules**        | CRUD complet, gestion des statuts (AVAILABLE → RESERVED → MAINTENANCE → RETIRED)               |
| **Utilisateurs**     | Gestion ADMIN/AGENT/DRIVER, soft delete, réactivation                                          |
| **Notifications**    | Alertes automatiques (nouvelle réservation, fin de réservation imminente, mise en maintenance) |

---

## Architecture générale

![Architecture générale](img/architecture_generale.png)

---

## Interactions entre les couches

![Interactions entre couches](img/interactions_couches.png)

---

## Backend — `parkyn-api`

### Stack technique

| Composant         | Technologie                         |
| ----------------- | ----------------------------------- |
| Langage           | Java 21                             |
| Framework         | Spring Boot 4.0.5                   |
| Base de données   | PostgreSQL 16                       |
| Migrations        | Flyway                              |
| Sécurité          | Spring Security + JWT (jjwt 0.12.6) |
| Documentation API | Springdoc OpenAPI / Swagger UI      |
| Tests             | JUnit 5 + Testcontainers            |
| Build             | Maven 3.9                           |
| Utilitaires       | Lombok, spring-dotenv               |

### Architecture applicative

```
parkyn-api/
├── controller/         # Couche REST — endpoints, validation HTTP, @PreAuthorize
├── service/            # Couche métier — règles, logique applicative
├── repository/         # Couche données — Spring Data JPA + Specifications
├── model/              # Entités JPA (User, Vehicle, Reservation, Notification, RefreshToken)
├── dto/                # Objets de transfert (Request / Response records)
├── security/           # JWT filter, SecurityConfig, UserDetailsService
└── resources/
    └── db/migration/   # Migrations Flyway versionnées
        ├── V1__init.sql                           # Schéma initial
        ├── V2__partial_unique_plate_number.sql    # Contrainte unicité plaque
        ├── V3__add_condition_to_vehicles.sql      # Condition véhicule
        └── V4__add_notifications.sql              # Table notifications
```

Architecture **3-couches stateless**. Chaque requête est authentifiée via un **JWT** signé — aucune session côté serveur.

### Rôles & permissions

| Rôle     | Périmètre                                                                             |
| -------- | ------------------------------------------------------------------------------------- |
| `ADMIN`  | Accès complet — gestion utilisateurs, véhicules, réservations, tableau de bord global |
| `AGENT`  | Créer des conducteurs, gérer les véhicules et réservations, statistiques personnelles |
| `DRIVER` | Aucun accès direct à l'API — géré par les agents                                      |

### API REST

Base URL : `http://localhost:8080/api/v1`

#### Authentification

| Méthode | Endpoint        | Accès       | Description                                       |
| ------- | --------------- | ----------- | ------------------------------------------------- |
| `POST`  | `/auth/login`   | Public      | Connexion — retourne access token + refresh token |
| `POST`  | `/auth/refresh` | Public      | Renouvellement de l'access token                  |
| `POST`  | `/auth/logout`  | Authentifié | Révocation du refresh token                       |
| `GET`   | `/auth/me`      | Authentifié | Profil de l'utilisateur connecté                  |

#### Utilisateurs

| Méthode  | Endpoint                 | Rôles        | Description                    |
| -------- | ------------------------ | ------------ | ------------------------------ |
| `GET`    | `/users`                 | ADMIN        | Liste paginée des utilisateurs |
| `POST`   | `/users`                 | ADMIN, AGENT | Créer un utilisateur           |
| `PATCH`  | `/users/{id}`            | ADMIN, AGENT | Modifier un utilisateur        |
| `DELETE` | `/users/{id}`            | ADMIN, AGENT | Désactiver (soft delete)       |
| `PATCH`  | `/users/{id}/reactivate` | ADMIN        | Réactiver un utilisateur       |
| `GET`    | `/users/drivers`         | ADMIN, AGENT | Liste paginée des conducteurs  |

#### Véhicules

| Méthode  | Endpoint                | Rôles        | Description                |
| -------- | ----------------------- | ------------ | -------------------------- |
| `GET`    | `/vehicles`             | ADMIN, AGENT | Liste paginée avec filtres |
| `POST`   | `/vehicles`             | ADMIN, AGENT | Ajouter un véhicule        |
| `GET`    | `/vehicles/{id}`        | ADMIN, AGENT | Détail d'un véhicule       |
| `PATCH`  | `/vehicles/{id}`        | ADMIN, AGENT | Modifier un véhicule       |
| `DELETE` | `/vehicles/{id}`        | ADMIN, AGENT | Supprimer (soft delete)    |
| `PATCH`  | `/vehicles/{id}/status` | ADMIN, AGENT | Changer le statut          |

#### Réservations

| Méthode | Endpoint                    | Rôles        | Description              |
| ------- | --------------------------- | ------------ | ------------------------ |
| `GET`   | `/reservations`             | ADMIN, AGENT | Liste paginée            |
| `POST`  | `/reservations`             | ADMIN, AGENT | Créer une réservation    |
| `GET`   | `/reservations/{id}`        | ADMIN, AGENT | Détail d'une réservation |
| `POST`  | `/reservations/{id}/return` | ADMIN, AGENT | Enregistrer le retour    |

#### Notifications & Dashboard

| Méthode | Endpoint                   | Rôles        | Description                    |
| ------- | -------------------------- | ------------ | ------------------------------ |
| `GET`   | `/notifications`           | ADMIN, AGENT | Notifications de l'utilisateur |
| `PATCH` | `/notifications/{id}/read` | ADMIN, AGENT | Marquer comme lue              |
| `PATCH` | `/notifications/read-all`  | ADMIN, AGENT | Tout marquer comme lu          |
| `GET`   | `/dashboard`               | ADMIN, AGENT | Données tableau de bord        |

### Flux d'authentification JWT

1. `POST /auth/login` → **access token** (1 h) + **refresh token** (1 jour)
2. Chaque requête protégée : `Authorization: Bearer <access_token>`
3. À expiration : `POST /auth/refresh` → nouvel access token
4. `POST /auth/logout` → révocation serveur du refresh token

### Schéma base de données

```
users                       vehicles
├── id (UUID PK)            ├── id (UUID PK)
├── first_name              ├── plate_number (unique si actif)
├── last_name               ├── brand / model / year
├── email (unique)          ├── mileage_km
├── hashed_password         ├── condition (EXCELLENT|GOOD|FAIR|POOR)
├── role (ADMIN|AGENT|DRIVER)├── status (AVAILABLE|RESERVED|MAINTENANCE|RETIRED)
├── is_active               ├── commissioned_at
└── created_at              ├── deleted_at
                            └── created_at

reservations                        refresh_tokens
├── id (UUID PK)                    ├── id (UUID PK)
├── vehicle_id → vehicles           ├── user_id → users
├── driver_id → users               ├── token (unique)
├── agent_id → users                ├── expires_at
├── start_date / end_date           ├── revoked_at
├── returned_at                     └── created_at
├── condition_before / condition_after
├── mileage_start / mileage_end
├── damage_reported / damage_details
└── created_at

notifications
├── id (UUID PK)
├── user_id → users
├── reservation_id → reservations (nullable)
├── type (NEW_RESERVATION|RESERVATION_ENDING_SOON|VEHICLE_MAINTENANCE)
├── is_read
└── created_at
```

### Tests

```bash
cd parkyn-api
mvn test
```

Les tests d'intégration utilisent **Testcontainers** (PostgreSQL) — Docker doit être disponible.

### Documentation Swagger UI

Accessible sur : `http://localhost:8080/swagger-ui/index.html`  
Aucune authentification requise pour consulter la documentation.

---

## Frontend — `parkyn-web`

### Stack technique

| Composant  | Technologie                             |
| ---------- | --------------------------------------- |
| Framework  | Angular 21                              |
| Style      | Tailwind CSS 4                          |
| Icons      | Heroicons                               |
| Réactivité | Signals, computed()                     |
| HTTP       | HttpClient + intercepteurs fonctionnels |
| Routing    | Angular Router (lazy loading)           |
| Build      | Angular CLI / @angular/build            |

### Architecture applicative

```
parkyn-web/src/app/
├── core/
│   ├── components/         # Header, Toast (composants partagés)
│   ├── interceptors/       # AuthInterceptor (JWT), ErrorInterceptor (toast global)
│   ├── services/           # AuthService, VehicleService, ReservationService...
│   └── models/             # Interfaces partagées (Page<T>)
├── features/
│   ├── dashboard/          # Tableau de bord (bento grid, stats rôle-dépendantes)
│   ├── reservations/       # Liste, création, détail, retour véhicule
│   ├── vehicles/           # Liste, détail, gestion statuts
│   ├── users/              # Gestion utilisateurs
│   └── profile/            # Profil utilisateur connecté
└── auth/                   # Page de connexion, guards
```

### Points techniques notables

- **Signals** : état réactif via `signal()` et `computed()` — pas de NgRx
- **Intercepteurs fonctionnels** : `AuthInterceptor` (injection automatique du Bearer token + refresh transparent), `ErrorInterceptor` (toast global pour toutes les erreurs HTTP)
- **Contrôle de flux moderne** : syntaxe `@if` / `@for` (Angular 17+)
- **Rôle-based UI** : tableau de bord et accès adaptés selon `ADMIN` ou `AGENT`

---

## DevOps — Docker & Reverse Proxy

### Services Docker Compose

| Service | Image / Build                             | Rôle                                  | Réseau             |
| ------- | ----------------------------------------- | ------------------------------------- | ------------------ |
| `db`    | `postgres:16`                             | Base de données PostgreSQL            | Interne uniquement |
| `api`   | `./parkyn-api` (multi-stage Maven)        | API Spring Boot                       | Interne            |
| `web`   | `./parkyn-web` (multi-stage Node + Nginx) | Serveur fichiers statiques Angular    | Interne            |
| `nginx` | `nginx:alpine`                            | Reverse proxy — point d'entrée unique | **:80 exposé**     |

### Reverse proxy Nginx

Nginx est le **seul point d'entrée public** (port 80). Il route le trafic vers les bons containers :

```
http://localhost/          →  web:80    (Angular)
http://localhost/api/...   →  api:8080  (Spring Boot)
```

Configuration : [`nginx/nginx.conf`](nginx/nginx.conf)

### Dockerfiles

- **`parkyn-api/Dockerfile`** : multi-stage (Maven build → JRE 21 Alpine), utilisateur non-root
- **`parkyn-web/Dockerfile`** : multi-stage (`npm ci` + `ng build` → Nginx Alpine)

---

## Guide d'installation

### Prérequis

- [Docker](https://www.docker.com/) & Docker Compose

### 1. Cloner le dépôt

```bash
git clone <url-du-repo>
cd parkyn
```

### 2. Configurer les variables d'environnement

```bash
cp .env.example .env
```

Renseigner le fichier `.env` :

| Variable                  | Description                             | Défaut             |
| ------------------------- | --------------------------------------- | ------------------ |
| `POSTGRES_DB`             | Nom de la base de données               | —                  |
| `POSTGRES_USER`           | Utilisateur PostgreSQL                  | —                  |
| `POSTGRES_PASSWORD`       | Mot de passe PostgreSQL                 | —                  |
| `JWT_SECRET`              | Clé secrète JWT (Base64, min. 256 bits) | —                  |
| `JWT_EXPIRATION_MS`       | Durée de vie access token (ms)          | `3600000` (1 h)    |
| `REFRESH_EXPIRATION_DAYS` | Durée de vie refresh token (jours)      | `1`                |
| `CORS_ALLOWED_ORIGINS`    | Origines autorisées                     | `http://localhost` |

Générer un `JWT_SECRET` sécurisé :

```bash
openssl rand -base64 64
```

### 3. Démarrer la stack complète

```bash
docker compose up -d --build
```

| URL                                           | Service                   |
| --------------------------------------------- | ------------------------- |
| `http://localhost`                            | Application Angular       |
| `http://localhost:8080/swagger-ui/index.html` | Documentation API Swagger |

### 4. Arrêter

```bash
docker compose down          # Arrêter les containers
docker compose down -v       # Arrêter + supprimer les volumes (⚠️ efface la DB)
```

### Développement local (sans Docker)

```bash
# Terminal 1 — API (PostgreSQL requis séparément)
cd parkyn-api
mvn spring-boot:run

# Terminal 2 — Frontend
cd parkyn-web
ng serve
```

---

## Images

### Login

![Login](img/login.png)

### Register

![Register](img/register.png)

### Dashboard

#### Dashboard Admin

![Dashboard Admin](img/dashboard_admin.png)

#### Dashboard Agent

![Dashboard Agent](img/dashboard_agent.png)

### Réservations

#### Liste des réservations

![Liste réservations](img/liste_reservations.png)

#### Créer une nouvelle réservation

![Nouvelle réservation](img/create_reservation.png)

#### Détails d'une réservation

![Détails réservation](img/detail_reservation_par_id.png)

### Véhicules

#### Liste des véhicules

![Liste véhicules](img/liste_vehicules.png)

#### Ajouter un nouveau véhicule

![Nouveau véhicule](img/create_vehicle.png)

#### Détails d'un véhicule

![Details véhicule](img/details_vehicule_par_id.png)

#### Mettre un véhicule véhicule en maintenance

![Maintenance véhicule](img/maintenance_vehicule.png)

### Utilisateurs

#### Liste des utilisateurs

![Liste utilisateurs](img/liste_users.png)

#### Créer un nouvel utilisateur

![Nouvel utilisateur](img/create_user.png)

#### Détails d'un utilisateur

![Détail utilisateur](img/details_user_par_id.png)

### Profil

#### Profil d'un ADMIN

![Profil Admin](img/profil_admin.png)

#### Profil d'un AGENT

![Profil Agent](img/profil_agent.png)

### Notifications

![Notifications](img/notifications.png)
