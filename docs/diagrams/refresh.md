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
    Note right of Navigateur: Le cookie refresh_token n'est pas envoyé<br/>(Path = /api/v1/auth/refresh)

    alt Access token valide
        API->>BDD: Révoque toutes les sessions de l'utilisateur (tous ses appareils)
        API-->>Navigateur: 204 + Set-Cookie refresh_token= #59; Max-Age=0
        Note right of Navigateur: Mêmes attributs et même Path :<br/>le navigateur supprime le cookie
    else Access token expiré ou absent
        API-->>Angular: 401 ou 403, rien n'est révoqué
        Note right of API: Limite connue, à traiter en C4<br/>(révocation à partir du cookie)
    end

    Angular->>Angular: Efface la session locale (finalize, succès ou échec)
    Angular-->>Navigateur: Redirection vers /login
```
