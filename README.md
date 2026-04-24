# Parkyn

Application de gestion de flotte de véhicules et de réservations.

---

## Backend — `parkyn-api`

### Stack technique

| Composant | Technologie |
|-----------|-------------|
| Langage | Java 21 |
| Framework | Spring Boot 4.0.5 |
| Base de données | PostgreSQL 16 |
| Migrations | Flyway |
| Sécurité | Spring Security + JWT (jjwt 0.12.6) |
| Documentation API | Springdoc OpenAPI / Swagger UI |
| Tests | JUnit 5 + Testcontainers |
| Build | Maven 3.9 |
| Utilitaires | Lombok, spring-dotenv |

### Architecture

```
parkyn-api/
├── controller/     # Couche REST — routing, validation, réponses HTTP
├── service/        # Couche métier — règles, autorisations
├── repository/     # Couche données — Spring Data JPA
├── model/          # Entités JPA
├── dto/            # Objets de transfert (request / response)
├── security/       # JWT filter, SecurityConfig, UserDetailsService
└── resources/
    └── db/migration/   # Scripts Flyway (V1, V2, ...)
```

Architecture 3-couches stateless. Authentification par **JWT** (access token court + refresh token).

---

### Prérequis

- **Docker & Docker Compose** (recommandé)
- Ou **Java 21** + **Maven 3.9** pour le développement local

---

### Configuration

Copier le fichier d'exemple et renseigner les variables :

```bash
cp .env.example .env
```

| Variable | Description | Défaut |
|----------|-------------|--------|
| `POSTGRES_DB` | Nom de la base de données | — |
| `POSTGRES_USER` | Utilisateur PostgreSQL | — |
| `POSTGRES_PASSWORD` | Mot de passe PostgreSQL | — |
| `JWT_SECRET` | Clé secrète JWT (Base64, min. 256 bits) | — |
| `JWT_EXPIRATION_MS` | Durée de vie de l'access token (ms) | `3600000` (1 h) |
| `REFRESH_EXPIRATION_DAYS` | Durée de vie du refresh token (jours) | `1` |
| `CORS_ALLOWED_ORIGINS` | Origines autorisées (séparées par `,`) | `http://localhost:5000` |

Générer un `JWT_SECRET` sécurisé :

```bash
openssl rand -base64 64
```

---

### Démarrage

#### Avec Docker Compose (recommandé)

Backend seul (db + api) :

```bash
docker compose up --build db api
```

Stack complète (db + api + web) :

```bash
docker compose up --build
```

L'API est accessible sur `http://localhost:8080`.

#### En local (développement)

```bash
# Démarrer PostgreSQL séparément, puis :
cd parkyn-api
mvn spring-boot:run
```

---

### API REST

Base URL : `http://localhost:8080/api/v1`

#### Authentification

| Méthode | Endpoint | Accès | Description |
|---------|----------|-------|-------------|
| `POST` | `/auth/login` | Public | Connexion — retourne access token + refresh token |
| `POST` | `/auth/refresh` | Public | Renouvellement de l'access token |
| `POST` | `/auth/logout` | Authentifié | Révocation du refresh token |
| `GET` | `/auth/me` | Authentifié | Profil de l'utilisateur connecté |

#### Utilisateurs

| Méthode | Endpoint | Rôles | Description |
|---------|----------|-------|-------------|
| `GET` | `/users` | ADMIN | Liste paginée des utilisateurs actifs |
| `POST` | `/users` | ADMIN, AGENT | Créer un utilisateur |
| `PATCH` | `/users/{id}` | ADMIN | Modifier un utilisateur |
| `DELETE` | `/users/{id}` | ADMIN, AGENT | Désactiver un utilisateur (soft delete) |
| `GET` | `/users/drivers` | ADMIN, AGENT | Liste paginée des conducteurs actifs |
| `PATCH` | `/users/{id}/reactivate` | ADMIN | Réactiver un utilisateur désactivé |

#### Véhicules

| Méthode | Endpoint | Rôles | Description |
|---------|----------|-------|-------------|
| `GET` | `/vehicles` | ADMIN, AGENT | Liste paginée des véhicules |
| `POST` | `/vehicles` | ADMIN, AGENT | Ajouter un véhicule |
| `GET` | `/vehicles/{id}` | ADMIN, AGENT | Détail d'un véhicule |
| `PATCH` | `/vehicles/{id}` | ADMIN, AGENT | Modifier un véhicule |
| `DELETE` | `/vehicles/{id}` | ADMIN, AGENT | Supprimer un véhicule (soft delete) |
| `PATCH` | `/vehicles/{id}/status` | ADMIN, AGENT | Changer le statut du véhicule |

#### Réservations

| Méthode | Endpoint | Rôles | Description |
|---------|----------|-------|-------------|
| `GET` | `/reservations` | ADMIN, AGENT | Liste paginée des réservations |
| `POST` | `/reservations` | ADMIN, AGENT | Créer une réservation |
| `GET` | `/reservations/{id}` | ADMIN, AGENT | Détail d'une réservation |
| `POST` | `/reservations/{id}/return` | ADMIN, AGENT | Enregistrer le retour d'un véhicule |

---

### Authentification JWT

Le flux d'authentification est le suivant :

1. `POST /auth/login` → retourne un **access token** (1 h par défaut) et un **refresh token** (1 jour par défaut)
2. Inclure l'access token dans chaque requête protégée : `Authorization: Bearer <token>`
3. Lorsque l'access token expire, appeler `POST /auth/refresh` avec le refresh token pour en obtenir un nouveau
4. `POST /auth/logout` révoque le refresh token côté serveur

---

### Rôles & permissions

| Rôle | Description |
|------|-------------|
| `ADMIN` | Accès complet — gestion des utilisateurs, véhicules et réservations |
| `AGENT` | Peut créer des conducteurs, gérer les véhicules et les réservations |
| `DRIVER` | Pas d'accès direct à l'API — ses données sont gérées par les agents |

Règles métier notables :
- Un **AGENT** ne peut créer que des comptes `DRIVER`
- Un **AGENT** ne peut désactiver que des `DRIVER`
- La réactivation d'un utilisateur est réservée à l'**ADMIN**

---

### Schéma base de données

```
users               vehicles
├── id (UUID PK)    ├── id (UUID PK)
├── first_name      ├── plate_number (unique si actif)
├── last_name       ├── brand / model / year
├── email (unique)  ├── mileage_km
├── hashed_pwd      ├── status (AVAILABLE | RESERVED | MAINTENANCE | RETIRED)
├── role            ├── commissioned_at
├── is_active       ├── deleted_at
└── created_at      └── created_at

reservations                    refresh_tokens
├── id (UUID PK)                ├── id (UUID PK)
├── vehicle_id → vehicles       ├── user_id → users
├── driver_id → users           ├── token (unique)
├── agent_id → users            ├── expires_at
├── start_date / end_date       ├── revoked_at
├── condition_before/after      └── created_at
├── mileage_start / mileage_end
├── damage_reported / details
└── returned_at / created_at
```

Migrations Flyway :
- `V1__init.sql` — schéma initial complet + index
- `V2__partial_unique_plate_number.sql` — contrainte d'unicité partielle sur la plaque (véhicules non supprimés)

---

### Tests

```bash
cd parkyn-api
mvn test
```

Les tests d'intégration utilisent **Testcontainers** (PostgreSQL) — Docker doit être disponible.

---

### Documentation API (Swagger UI)

Disponible à l'adresse : `http://localhost:8080/swagger-ui.html`

L'accès au Swagger UI ne requiert pas d'authentification.
