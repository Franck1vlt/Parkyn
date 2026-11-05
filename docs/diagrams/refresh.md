# Séquence : refresh

```mermaid
sequenceDiagram
actor Navigateur
participant Angular
participant API as API Spring
participant BDD

    Angular->>API: Requêtes API + access token (plusieurs en parallèle)
    API-->>Angular: 401 Unauthorized (pour chacune)
    alt Le token a déjà changé depuis l'envoi de la requête
        Angular->>API: Rejoue la requête avec le token courant, sans refresh
    end
    Note over Angular: Un seul refresh partagé entre toutes les requêtes en 401<br/>(les autres attendent son résultat)
    Angular->>API: POST /api/v1/auth/refresh + en-tête X-XSRF-TOKEN
    Note right of Navigateur: Le navigateur joint automatiquement<br/>les cookies refresh_token (HttpOnly) et XSRF-TOKEN.<br/>Angular recopie XSRF-TOKEN dans l'en-tête

    alt En-tête X-XSRF-TOKEN absent ou différent du cookie
        API-->>Angular: 403 Forbidden (CsrfFilter, refresh token non lu ni consommé)
    end

    API->>API: Extrait le refresh token du cookie
    API->>API: Hash du refresh token
    API->>BDD: Recherche user_session correspondante
    BDD-->>API: Session + user_id + expires_at + revoked_at

    alt Token inexistant
        API-->>Angular: 401 Unauthorized
        Angular-->>Navigateur: Session invalide → reconnexion
    else Token déjà révoqué, moins de 10 s après la rotation
        Note right of API: Période de grâce (parkyn.session.reuse-grace) :<br/>course légitime (deux onglets), la famille reste active
        API-->>Angular: 401 Unauthorized
    else Token déjà révoqué, plus de 10 s après la rotation
        API->>BDD: Révoque toutes les sessions de la même famille
        Note right of API: Réutilisation d'un refresh token déjà tourné<br/>→ vol possible, on coupe toute la chaîne
        API-->>Angular: 401 Unauthorized
        Angular-->>Navigateur: Session invalide → reconnexion
    else Token expiré
        API->>BDD: Révoque la session
        API-->>Angular: 401 Unauthorized
        Angular-->>Navigateur: Session expirée → reconnexion
    else Utilisateur non actif
        API->>BDD: Révoque la session
        API-->>Angular: 401 Unauthorized
        Angular-->>Navigateur: Compte indisponible → reconnexion
    else Token valide
        API->>API: Génère nouvel access token
        API->>API: Génère nouveau refresh token
        API->>API: Hash le nouveau refresh token

        API->>BDD: UPDATE … SET revoked_at WHERE id = ? AND revoked_at IS NULL
        Note right of BDD: Atomique : entre deux refresh simultanés,<br/>un seul modifie la ligne. 0 ligne → 401
        API->>BDD: Crée une nouvelle user_session (même famille)
        Note right of BDD: Ancien refresh token → revoked_at<br/>Nouveau refresh token → actif

        API-->>Navigateur: Set-Cookie nouveau refresh_token
        API-->>Angular: 200 OK + nouvel access token
        Angular->>API: Rejoue les requêtes en attente
        API-->>Angular: Réponses (une erreur du rejeu, 409 par exemple, remonte telle quelle)
    end
    Note over Angular: Refresh refusé (401 ou 403) : un seul logout,<br/>chaque requête en attente reçoit son 401 d'origine
```

Tous les `401` ont le même corps (« Session expirée, veuillez vous reconnecter ») : le client ne distingue pas un token inconnu, révoqué, expiré ou un compte désactivé.

## État de la mise en œuvre

- **Fait (C1) :** le token est lu dans le cookie uniquement (cookie absent = même `401`, token dans le corps ignoré) ; rotation avec révocation de l'ancien token et nouveau `Set-Cookie` ; refus uniformes.
- **Fait (C2) :** protection CSRF par double-submit (`403` sans en-tête ou avec une valeur fausse) ; révocation atomique de l'ancien token (`revokeIfActive`), un seul refresh réussit parmi des requêtes simultanées ; côté Angular, refresh partagé, rejeu sans refresh si le token a déjà changé, un seul logout en cas d'échec.
- **Fait (C3) :** `user_sessions` (empreinte SHA-256 seulement, token de 256 bits), rotation dans la même famille, session expirée ou compte non actif révoqué en base avant le `401` (`noRollbackFor = SessionRejectedException.class`), révocation de toute la famille en cas de réutilisation au-delà de la période de grâce (10 s). `refresh_tokens` n'est plus utilisée.
- **Limite connue (grâce) :** si un voleur fait la rotation avant l'utilisateur et que celui-ci représente l'ancien token dans les 10 s, la famille n'est pas révoquée et le voleur garde son nouveau token. Grâce à retirer quand Angular aura un verrou entre onglets (phase D).

# Séquence : logout

```mermaid
sequenceDiagram
actor Navigateur
participant Angular
participant API as API Spring
participant BDD

    Navigateur->>Angular: Clic « Déconnexion »
    Angular->>API: POST /api/v1/auth/logout + access token
    Note right of Navigateur: Le navigateur joint le cookie refresh_token<br/>(Path = /api/v1/auth depuis C4)

    alt Access token valide
        API->>BDD: Cherche la session du cookie (empreinte)
        alt Session de l'utilisateur authentifié
            API->>BDD: Révoque sa famille (cet appareil seulement)
        else Pas de cookie, ou cookie d'un autre utilisateur
            Note right of API: Rien n'est révoqué
        end
        API-->>Navigateur: 204 + Set-Cookie refresh_token= #59; Max-Age=0
        Note right of Navigateur: Mêmes attributs et même Path :<br/>le navigateur supprime le cookie
    else Access token expiré ou absent
        API-->>Angular: 401 ou 403, rien n'est révoqué
        Note right of Angular: Phase D : refresh puis rejeu du logout<br/>(aujourd'hui /auth/logout est exclu du refresh)
    end

    Angular->>Angular: Efface la session locale (finalize, succès ou échec)
    Angular-->>Navigateur: Redirection vers /login
```

# Séquence : sessions actives (C4)

```mermaid
sequenceDiagram
actor Navigateur
participant Angular
participant API as API Spring
participant BDD

    Angular->>API: GET /api/v1/auth/sessions + access token
    Note right of Navigateur: Cookie refresh_token joint :<br/>désigne l'appareil courant
    API->>BDD: Sessions actives de l'utilisateur (une par famille) + date de connexion de chaque famille
    API-->>Angular: 200 [id = family_id, user_agent, ip, signed_in_at, last_active_at, current]

    alt Déconnecter un appareil
        Angular->>API: DELETE /api/v1/auth/sessions/{id} + access token
        API->>BDD: UPDATE … WHERE family_id = ? AND user_id = utilisateur AND revoked_at IS NULL
        alt 0 ligne (inconnue, autre utilisateur, autre tenant, déjà révoquée)
            API-->>Angular: 404 « Session introuvable »
        else
            API-->>Angular: 204
        end
    else Déconnecter les autres appareils
        Angular->>API: DELETE /api/v1/auth/sessions + access token
        API->>BDD: Révoque toutes les familles de l'utilisateur sauf celle du cookie (toutes sans cookie)
        API-->>Angular: 204
    end
```

- Accès : `isAuthenticated()`, comme `/auth/me` et `/auth/logout` : chacun gère ses propres sessions, quel que soit son rôle (exception assumée à la règle « une permission par endpoint », voir ADR-0002).
- User agent et IP affichés : ceux du dernier refresh de l'appareil (chaque refresh crée la session suivante de la famille). `last_used_at` n'est pas utilisée.
- Désactivation d'un compte (`DELETE /users/{id}` ou `PATCH` avec `active: false`) : toutes ses sessions sont révoquées immédiatement.
- Délai d'invalidation : une session révoquée ne peut plus faire de refresh, mais son access token reste valide jusqu'à son expiration (15 min au plus). Seule la désactivation coupe tout de suite (`isEnabled()` dans `JwtAuthFilter`). Vérification d'un `sid` à chaque requête : évolution future (ADR-0001).
