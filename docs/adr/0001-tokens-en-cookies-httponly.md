# ADR-0001 : Tokens en cookies HttpOnly

- Statut : Accepté
- Date : 2026-10-05

## Contexte

### Le Problème

Actuellement, les tokens d'authentification sont stockés dans `localStorage`.

Cette approche présente un risque en cas de faille **XSS (Cross-Site Scripting)** : le code JavaScript injecté dans l'application peut accéder au contenu de `localStorage` et exfiltrer les tokens.

Un attaquant pourrait alors récupérer un token valide et l'utiliser pour accéder à l'API au nom de l'utilisateur.

L'objectif est donc de réduire l'exposition des tokens au JavaScript côté client, tout en conservant un mécanisme de rafraîchissement des sessions.

### Les Contraintes

- Le frontend est développé avec Angular.
- L'API est séparée du frontend et communique via HTTP.
- L'authentification repose sur un access token et un refresh token.
- Le refresh token doit avoir une durée de vie plus longue que l'access token.
- Le frontend ne doit pas avoir accès directement au refresh token.
- Les requêtes authentifiées doivent fonctionner avec une API située sur une origine différente.
- La solution doit limiter les risques XSS et CSRF.
- Le mécanisme doit rester compatible avec les intercepteurs Angular et CORS.

## Options étudiées

### Option 1 — Conserver les tokens dans `localStorage`

Les access token et refresh token restent stockés dans `localStorage` et sont récupérés par l'application Angular.

#### Avantages

- Implémentation simple.
- Facilement accessible depuis les intercepteurs Angular.
- Pas de configuration particulière des cookies ou de CORS avec credentials.

#### Inconvénients

- Les tokens sont accessibles à JavaScript.
- Une faille XSS peut permettre leur lecture et leur exfiltration.
- Le refresh token étant généralement long-lived, son vol peut permettre une compromission durable de la session.

---

### Option 2 — Stocker les tokens dans des cookies `HttpOnly`

Les tokens sont stockés dans des cookies avec notamment :

- `HttpOnly` pour empêcher leur lecture par JavaScript.
- `Secure` pour limiter leur transmission à HTTPS.
- `SameSite` pour réduire les risques CSRF.

Angular ne lit plus directement les tokens. Le navigateur les transmet automatiquement lorsque les conditions du cookie sont respectées.

#### Avantages

- JavaScript ne peut pas lire un cookie `HttpOnly`.
- Réduit fortement le risque de vol direct du token via XSS.
- La gestion des cookies est assurée par le navigateur.
- Possibilité de limiter l'envoi d'un cookie avec `SameSite` et `Path`.

#### Inconvénients

- Les cookies introduisent un risque CSRF qu'il faut traiter.
- Configuration CORS nécessaire lorsque le frontend et l'API sont sur des origines différentes.
- Angular doit utiliser `withCredentials`.
- Le backend doit autoriser explicitement les credentials avec `allowCredentials`.
- Le token n'est plus directement accessible par l'application frontend.

---

### Option 3 — Access token en mémoire + refresh token en cookie `HttpOnly`

L'access token est conservé uniquement en mémoire côté Angular et envoyé dans le header :

`Authorization: Bearer <access_token>`

Le refresh token est stocké dans un cookie `HttpOnly`, `Secure`, avec un `SameSite` adapté et un `Path` limité à l'endpoint de refresh.

```http
Set-Cookie: refresh_token=...;
            HttpOnly;
            Secure;
            SameSite=Lax;
            Path=/api/v1/auth/refresh
```

#### Avantages

- Le refresh token, qui possède une durée de vie longue, n'est pas accessible à JavaScript.
- L'access token n'est pas persistant dans localStorage.
- Une attaque XSS ne peut pas directement lire le refresh token.
- Le Path limite les requêtes auxquelles le refresh token est envoyé.
- L'access token reste facilement utilisable par l'intercepteur Angular via le header Authorization.

#### Inconvénients

- Le risque CSRF lié au cookie doit être pris en compte.
- Configuration CORS avec credentials nécessaire pour l'endpoint de refresh si les origines sont concernées.
- L'access token disparaît lors d'un rechargement complet de la page et doit être régénéré via le refresh token.
- L'implémentation est plus complexe qu'un stockage uniquement dans localStorage.

## Décision

Nous choisissons de conserver l'access token uniquement en mémoire côté Angular et de stocker le refresh token dans un cookie HttpOnly, Secure, avec un SameSite adapté et un Path limité à l'endpoint /api/v1/auth/refresh.

## Conséquences

### Conséquences positives

- Le refresh token n'est plus accessible depuis JavaScript.
- Une faille XSS ne permet plus de lire le refresh token via document.cookie (elle reste dangereuse, voir « Limites assumées »).
- L'access token n'est plus persistant dans localStorage.
- Le refresh token est envoyé uniquement vers l'endpoint prévu grâce au Path.
- Secure garantit que le cookie n'est transmis qu'en HTTPS.
- SameSite réduit le risque d'attaques CSRF.
- La durée de vie de l'access token peut rester courte tout en permettant de maintenir la session grâce au refresh token.

### Conséquences négatives

- L'authentification devient plus complexe à implémenter.
- Angular doit utiliser withCredentials pour les requêtes concernées.
- L'API doit configurer correctement CORS avec allowCredentials.
- Il faut prendre en compte le risque CSRF introduit par l'utilisation des cookies.
- L'access token étant conservé uniquement en mémoire, un rechargement de l'application nécessite un appel de refresh.
- La configuration des cookies (HttpOnly, Secure, SameSite, Path) doit être correctement maîtrisée.

### Limites assumées

- **`HttpOnly` ne protège pas contre XSS, il empêche seulement le JavaScript injecté de lire le refresh token.** Un script injecté peut toujours appeler l'API pendant que la page est ouverte, et lire l'access token en mémoire. Le gain : l'attaque est limitée à la durée de vie de l'access token (1 h) et à la session ouverte, au lieu d'une compromission durable (refresh token volé, 1 jour).
- **`SameSite` traite un problème différent : l'envoi automatique du cookie par une requête forgée (CSRF).** Seul `/api/v1/auth/refresh` reçoit le cookie, et un attaquant ne peut pas lire sa réponse (CORS) : le risque résiduel est faible, mais il doit être testé.
- **`SameSite` raisonne en _site_, pas en origine.** Avec `Lax`, le cookie n'est envoyé que si le frontend et l'API sont sur le même site (`app.parkyn.fr` et `api.parkyn.fr`, ou `localhost` avec des ports différents). Sur deux domaines distincts, il faudrait `SameSite=None; Secure`, avec une protection CSRF explicite.
- **Le `Path` doit correspondre à la vraie route.** L'API expose `/api/v1/auth/refresh`, pas `/auth/refresh`. Le logout doit aussi recevoir le cookie pour révoquer la session : à traiter (Path `/api/v1/auth` ou cookie effacé côté serveur).

On accepte donc une configuration plus complexe (cookies + CORS + CSRF) pour réduire l'exposition du token à longue durée de vie.

## Mise en œuvre (C1, 2026-10-30)

- **Cookie retenu :** `refresh_token`, `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth/refresh`, `Max-Age` égal à la durée du refresh token (`RefreshTokenCookie`).
- **`Strict` plutôt que `Lax` :** le frontend et l'API sont servis par la même origine (`/` et `/api/` derrière nginx). Le cookie n'a jamais à partir depuis un autre site, même lors d'une navigation. À revoir si l'API passe sur un autre site.
- **Logout :** le cookie n'est pas envoyé à `/api/v1/auth/logout` (Path restreint). Le logout révoque les refresh tokens à partir de l'utilisateur authentifié par l'access token, puis efface le cookie côté serveur (`Max-Age=0`, mêmes attributs). Limite : sans access token valide, rien n'est révoqué ; la révocation à partir du cookie est reportée à C4 (Path élargi à `/api/v1/auth` ou logout sous `/refresh`).
- **Corps de la réponse :** le login et le refresh ne renvoient plus le refresh token, seulement l'access token et les dates d'expiration. Un refresh token envoyé dans un corps JSON est ignoré.
- **Pas encore fait :** access token en mémoire côté Angular (phase D), hachage du token et détection de réutilisation (C3), protection CSRF (C2). `Secure` impose HTTPS en production.
