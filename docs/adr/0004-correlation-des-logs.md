# ADR-0004 : Corrélation des logs par identifiant de requête

- Statut : Accepté
- Date : 2026-11-06

## Contexte

Quand un utilisateur d'une entreprise rencontre une erreur, il faut retrouver la requête dans les logs sans la reproduire. Le front affichera une « référence » copiable.

## Décision

- Un identifiant par requête, en-tête `X-Request-Id`. nginx le génère (`$request_id`, `proxy_set_header` dans `/api/`, qui remplace toute valeur du client). `RequestIdFilter` le valide (`[A-Za-z0-9-]{8,64}`) ; une valeur absente ou douteuse (trop longue, retour à la ligne, HTML…) est remplacée par un UUID. L'API ne dépend donc jamais de nginx (`ng serve`, Traefik en production).
- Le filtre passe avant Spring Security (`HIGHEST_PRECEDENCE`) : l'en-tête est sur toutes les réponses, `401`, `403` et `429` compris.
- MDC : `requestId` (filtre), `tenantId` et `userId` (`JwtAuthFilter`, après l'authentification). Format : `logging.pattern.level`. Jamais d'email, de mot de passe, de token ni de corps de requête. Le MDC est vidé à la fin de chaque requête.
- Chaque réponse `4xx` ou `5xx` laisse une ligne `ERROR` : méthode, chemin (jamais la query string), statut. Une exception non gérée est loggée avec sa classe seulement (son message peut contenir des données).
- Même origine via nginx : l'en-tête est lisible par le front sans configuration CORS. Si le front appelle une autre origine : `exposedHeaders = X-Request-Id, Retry-After` dans `SecurityConfig`.

## Conséquences

- Toutes les erreurs client sont en `ERROR`, y compris un `401` d'access token expiré : à revoir si les logs deviennent bruyants.
- Pas de code d'erreur stable : seul le statut HTTP est loggé.
- Le MDC ne suit pas les threads asynchrones (envoi d'emails) : leurs logs n'ont pas l'identifiant.
- Les erreurs générées par nginx lui-même (`502`) n'ont ni l'en-tête ni de trace côté API.
