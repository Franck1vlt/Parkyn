# Schéma lot 1a

Les diagrammes montrent les clés et les colonnes ajoutées ou modifiées au lot 1a. Les colonnes existantes sans changement (noms, marque, kilométrage, etc.) ne sont pas répétées.

# Rôles et permissions

```mermaid
erDiagram

    tenants {
        UUID id PK
        VARCHAR name
        TIMESTAMPTZ created_at
    }

    roles {
        UUID id PK
        UUID tenant_id FK "NULL = rôle système"
        VARCHAR name "unique parmi les rôles système, et unique par tenant"
        TIMESTAMPTZ created_at
    }

    permissions {
        UUID id PK
        VARCHAR code UK
        VARCHAR scope "TENANT ou PLATFORM"
        VARCHAR description
    }

    role_permissions {
        UUID role_id PK, FK
        UUID permission_id PK, FK
    }

    user_roles {
        UUID user_id PK, FK
        UUID role_id PK, FK
    }

    tenants ||--o{ roles : "possède"
    roles ||--o{ role_permissions : "possède"
    permissions ||--o{ role_permissions : "attribuée à"
    users ||--o{ user_roles : "possède"
    roles ||--o{ user_roles : "attribué à"
```

Contraintes de `roles.name` (deux index uniques partiels) :

- `UNIQUE (name) WHERE tenant_id IS NULL` pour les rôles système.
- `UNIQUE (tenant_id, name) WHERE tenant_id IS NOT NULL` pour les rôles d'un tenant.

# Utilisateurs et authentification

```mermaid
erDiagram

    users {
        UUID id PK
        UUID tenant_id FK
        VARCHAR email UK "unique sans tenir compte de la casse"
        VARCHAR hashed_pwd "nom conservé, hash Argon2id ou BCrypt"
        VARCHAR status "PENDING_EMAIL_VERIFICATION, ACTIVE, DISABLED"
        VARCHAR role "TRANSITOIRE, supprimée au Contract"
        BOOLEAN is_active "TRANSITOIRE, supprimée au Contract"
        TIMESTAMPTZ created_at
    }

    verification_tokens {
        UUID id PK
        UUID user_id FK
        VARCHAR token_hash UK "hash du token du lien, jamais le token"
        TIMESTAMPTZ expires_at
        TIMESTAMPTZ consumed_at
        TIMESTAMPTZ created_at
    }

    user_sessions {
        UUID id PK
        UUID user_id FK
        VARCHAR refresh_token_hash UK
        UUID family_id "révocation en chaîne"
        VARCHAR user_agent
        VARCHAR ip
        TIMESTAMPTZ last_used_at
        TIMESTAMPTZ expires_at
        TIMESTAMPTZ revoked_at
        TIMESTAMPTZ created_at
    }

    tenants ||--o{ users : "contient"
    users ||--o{ verification_tokens : "possède"
    users ||--o{ user_sessions : "possède"
```

Contraintes et index :

- **Email :** `UNIQUE (lower(email))`. La colonne reste nullable uniquement pour les comptes existants sans email, qui sont désactivés à la migration : `CHECK (email IS NOT NULL OR status = 'DISABLED')`.
- **Statut :** `CHECK (status IN ('PENDING_EMAIL_VERIFICATION', 'ACTIVE', 'DISABLED'))`.
- **Conversion :** `is_active = TRUE` donne `ACTIVE`, `is_active = FALSE` donne `DISABLED`, et tout utilisateur sans email devient `DISABLED`.
- **Sessions :** index sur `(user_id, revoked_at)` et sur `family_id`.
- **Rôles :** index inverse `(role_id, user_id)` sur `user_roles`, pour lister les utilisateurs d'un rôle.
- **Anciennes sessions :** les lignes de `refresh_tokens` ne sont pas migrées. Les utilisateurs se reconnectent au déploiement, et la table est supprimée au Contract.

# Ressources métier

```mermaid
erDiagram

    users {
        UUID id PK
        UUID tenant_id FK
    }

    vehicles {
        UUID id PK
        UUID tenant_id FK
        VARCHAR plate_number
        TIMESTAMPTZ created_at
    }

    reservations {
        UUID id PK
        UUID tenant_id FK
        UUID driver_id FK "propriétaire, NOT NULL"
        UUID agent_id FK "agent référent, devient nullable"
        UUID vehicle_id FK
        UUID created_by FK "qui a créé, nullable"
        UUID updated_by FK "dernier modificateur, nullable"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    notifications {
        UUID id PK
        UUID tenant_id FK
        UUID user_id FK
        TIMESTAMPTZ created_at
    }

    tenants ||--o{ users : "contient"
    tenants ||--o{ vehicles : "contient"
    tenants ||--o{ reservations : "contient"
    tenants ||--o{ notifications : "contient"

    users ||--o{ reservations : "conducteur (driver_id)"
    users ||--o{ reservations : "agent référent (agent_id)"
    users ||--o{ reservations : "créateur (created_by)"
    users ||--o{ reservations : "modificateur (updated_by)"

    vehicles ||--o{ reservations : "réservé par"
    users ||--o{ notifications : "reçoit"
```

Règles de suppression des clés étrangères :

| Clé | Règle | Raison |
|---|---|---|
| `reservations.driver_id` | `RESTRICT` | Propriétaire de la réservation, et base de la portée `own`. On désactive et on anonymise un compte, on ne le supprime pas. |
| `reservations.agent_id`, `created_by`, `updated_by` | `SET NULL` | Colonnes nullables : l'historique reste, la référence disparaît. |
| `user_roles.user_id` | `CASCADE` | Sans utilisateur, l'association n'a plus de sens. |
| `verification_tokens.user_id`, `user_sessions.user_id` | `CASCADE` | Même raison. |

Backfill des réservations existantes : `created_by` reçoit la valeur de `agent_id`.
